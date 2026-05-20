package a;

public class Testing {
    public static void test() {
        Container c = new Container();
        c.<caret>
    }
}

// WITH_ORDER
// EXIST: component1
// EXIST: zMethod
