package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.getFoo<caret>
    }
}
// EXIST: getFoo
// ABSENT: getBar
// ABSENT: setFoo
// ABSENT: setBar
