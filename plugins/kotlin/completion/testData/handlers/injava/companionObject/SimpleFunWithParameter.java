package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ELEMENT: Companion.foo
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON

