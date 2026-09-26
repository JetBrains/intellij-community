package a;
public class Testing {
    public static void test() {
        String s = "hello";
        s.<caret>
    }
}

// ELEMENT: tagify
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON
