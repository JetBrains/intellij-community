package a;
public class Testing {
    public static void test() {
        Target target = new Target();
        target.<caret>
    }
}

// ELEMENT: format
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON
