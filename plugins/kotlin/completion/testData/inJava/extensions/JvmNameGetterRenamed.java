package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.total<caret>
    }
}
// EXIST: total
// ABSENT: getCount
