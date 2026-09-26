package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.assign<caret>
    }
}
// EXIST: assign
// ABSENT: setAmount
