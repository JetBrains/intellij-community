package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ABSENT: Companion.staticMethod
// EXIST: staticMethod
// EXIST: Companion.normalMethod
