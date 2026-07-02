package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.same<caret>
    }
}
// EXIST: same
