package a;

public class Testing {
    public static void test() {
        "".<caret>
    }
}

// WITH_ORDER
// EXIST: toLowerCase
// EXIST: hexToByteArray
