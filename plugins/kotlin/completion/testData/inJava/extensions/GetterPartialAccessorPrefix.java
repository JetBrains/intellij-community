package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.getVal<caret>
    }
}
// EXIST: getValue
// ABSENT: setValue
