package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.setVal<caret>
    }
}
// EXIST: setValue
// ABSENT: getValue
