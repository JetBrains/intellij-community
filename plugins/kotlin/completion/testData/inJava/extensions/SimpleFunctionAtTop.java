package a;

public class Testing {
    static int element = 0;
    public static void test(Greeter greeter) {
        greeter.g<caret>
        element = element + 1;
    }
}
// EXIST: greet