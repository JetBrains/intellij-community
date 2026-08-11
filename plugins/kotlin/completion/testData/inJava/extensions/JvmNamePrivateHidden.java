package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.renamedPrivate<caret>
    }
}
// ABSENT: renamedPrivate
