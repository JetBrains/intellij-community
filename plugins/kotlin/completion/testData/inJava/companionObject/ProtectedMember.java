package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ABSENT: Companion.protectedMethod
// EXIST: Companion.publicMethod
