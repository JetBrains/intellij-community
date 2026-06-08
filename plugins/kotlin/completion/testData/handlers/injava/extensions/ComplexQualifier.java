package a;
public class Testing {
    public static void test() {
        new Builder().build().<caret>
    }
}

// ELEMENT: display
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON
