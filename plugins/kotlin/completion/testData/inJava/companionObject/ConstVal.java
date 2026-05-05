package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ABSENT: Companion.getMaxRetries
// EXIST: MAX_RETRIES
// EXIST: Companion.getNormalProp
