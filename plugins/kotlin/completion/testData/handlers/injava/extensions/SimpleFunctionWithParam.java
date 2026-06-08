package a;
public class Testing {
    public static void test() {
        Target target = new Target();
        target.<caret>
    }
}

// ELEMENT: repeat
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON
