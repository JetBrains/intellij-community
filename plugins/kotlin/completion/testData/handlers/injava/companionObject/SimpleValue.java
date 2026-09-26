package a;

public class Testing {
    public static void test() {
        Test.<caret>
    }
}

// ELEMENT: Companion.getValue
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON

