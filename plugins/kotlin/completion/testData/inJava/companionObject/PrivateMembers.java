package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ABSENT: Companion.secret
// ABSENT: Companion.getHiddenValue
// EXIST: Companion.visible
// EXIST: Companion.getPublicValue
