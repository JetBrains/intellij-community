package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.setFoo<caret>
    }
}
// EXIST: setFoo
// ABSENT: setBar
// ABSENT: getFoo
// ABSENT: getBar
