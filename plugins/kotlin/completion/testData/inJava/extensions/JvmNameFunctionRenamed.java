package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.shout<caret>
    }
}
// EXIST: shout
