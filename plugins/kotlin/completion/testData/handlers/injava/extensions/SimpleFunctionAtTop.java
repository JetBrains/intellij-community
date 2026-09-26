package a;
public class Testing {
    static int element = 0;
    public static void test(Greeter greeter) {
        greeter.<caret>
        element = element + 1;
    }
}

// ELEMENT: greet
// AUTOCOMPLETE_SETTING: true
// FIR_IDENTICAL
// FIR_COMPARISON
