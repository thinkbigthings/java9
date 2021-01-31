package org.thinkbigthings.demo.records;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toList;
import static org.thinkbigthings.demo.records.Functional.uncheck;
import static org.thinkbigthings.demo.records.Try.*;

public class RecordStreamsTest {

    @Test
    public void basicStream() {

        // People sometimes use Map.Entry to handle simple pairs, including inside Streams.
        // Records make this much more usable.

        record Charge(double amount, double tax) {
            public Charge add(Charge other){
                return new Charge(amount + other.amount, tax + other.tax);
            }
        }

        List<Charge> itemizedCharges = List.of(
                new Charge(1,2),
                new Charge(3,4),
                new Charge(5,6));

        // one idea to get the totals
        double totalAmount = itemizedCharges.stream()
                .map(Charge::amount)
                .reduce(0.0, Double::sum);

        double totalTax = itemizedCharges.stream()
                .map(Charge::tax)
                .reduce(0.0, Double::sum);


        // this is very readable, at the cost of adding a new method to Charge.
        // Doesn't require any records or cryptic stream techniques
        // The data is the same but the MEANING might be a little different (total vs itemized)
        Charge total = itemizedCharges.stream()
                .reduce(new Charge(0,0), Charge::add);


        // Yes, this is the same "shape" as Charge, but this new type indicates the meaning, not just the data.
        // The cost of a duplicate data structure is trivial enough that we can do this because why not.
        record Total(double amount, double tax){}

        // records make a great merger for teeing operations
        Total total2 = itemizedCharges.stream()
                .collect(teeing(summingDouble(Charge::amount), summingDouble(Charge::tax), Total::new));

    }

    @Test
    public void testJoiningData() {

        // introduce new data during the stream and keep passing it along

        var words = List.of("here", "is", "a", "word", "list");

        // using "var" here allows us to assign to a real type (not Object, but anonymous)
        // this is allowed via LVTI introduced in Java 10, but it's... cumbersome
        var longWords = words.stream()
                .map(element -> new Object() {
                    String word = element;
                    int length = element.length();
                    Instant processedTimestamp = Instant.now();
                })
                .filter(t -> t.length > 3)
                .collect(toList());

//        longNames.iterator().next().
        System.out.println(longWords);

        // using an explicit type during processing makes the same code more readable
        // and we have an explicit type at the end which is easier to reason about
        record ProcessedWord(String word, int length, Instant recorded) {}

        var longWords2 = words.stream()
                .map(word -> new ProcessedWord(word, word.length(), Instant.now()))
                .filter(word -> word.length > 3)
                .collect(toList());

//        longNames2.iterator().next().
        System.out.println(longWords2);

    }

    @Test
    public void testStreamExceptions() {


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

        List<String> dates = List.of("2021-06-21", "whoops", "2001-12-21");

        // code reviewers hate this one simple trick
        try {
            dates.stream()
                    .map(uncheck(format::parse))
                    .collect(toList());
        }
        catch(Exception e) {
            e.printStackTrace();
        }


        // use the Try object to attempt every element in the stream
        // and save the exceptions and results to separate lists

        var tries = dates.stream()
                .map(tryCatch(format::parse))
                .collect(toList());

        var exceptions = tries.stream()
                .map(Try::exception)
                .filter(Objects::nonNull)
                .collect(toList());

        var successes = tries.stream()
                .map(Try::result)
                .filter(Objects::nonNull)
                .collect(toList());


        // use the Try object to attempt every element in the stream
        // and save the exceptions and results in a single step

        // this works, but we have to look back at the collector to remember what the Boolean means
        // and we'd still have to extract exceptions or results from the two lists
        var attempts = dates.stream()
                .map(tryCatch(format::parse))
                .collect(partitioningBy(t -> t.exception() != null));


        // again, records are good mergers for teeing
        record Results<T>(List<? extends Exception> exceptions, List<T> results) {}

        Results<Date> c = dates.stream()
                .map(tryCatch(format::parse))
                .collect(teeing( toExceptions(), toResults(), Results::new));


    }
}