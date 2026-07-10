package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.customSetter<caret>
    }
}
// EXIST: customSetter
// ABSENT: setValue
