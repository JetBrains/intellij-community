package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ABSENT: Companion.suspendMethod
// EXIST: Companion.nonSuspendMethod
