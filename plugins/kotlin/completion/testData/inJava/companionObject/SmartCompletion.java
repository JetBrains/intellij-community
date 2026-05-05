package a;

public class Testing {
    public static void test() {
        String s = Test.<caret>
    }
}

// COMPLETION_TYPE: SMART
// EXIST: Companion.createString
// ABSENT: Companion.createInt
