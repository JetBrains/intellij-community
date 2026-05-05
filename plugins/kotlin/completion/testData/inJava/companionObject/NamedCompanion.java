package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// EXIST: Factory.create
// ABSENT: Companion.create
