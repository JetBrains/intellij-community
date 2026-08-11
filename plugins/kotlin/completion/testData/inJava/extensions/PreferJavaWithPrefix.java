package a;

public class Testing {
    public static void test() {
        "".a<caret>
    }
}

// WITH_ORDER
// EXIST: toLowerCase
// EXIST: hexToByteArray
