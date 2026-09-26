package a;

import kotlin.Triple;

public class Testing {
    public static void test() {
        Triple<String, Integer, Boolean> triple = new Triple<>("hello", 42, true);
        triple.<caret>
    }
}

// WITH_ORDER
// EXIST: getFirst
// EXIST: getSecond
// EXIST: getThird
// EXIST: component1
// EXIST: component2
// EXIST: component3
