package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ABSENT: Companion.getDirectField
// EXIST: directField
// EXIST: Companion.getNormalProp
