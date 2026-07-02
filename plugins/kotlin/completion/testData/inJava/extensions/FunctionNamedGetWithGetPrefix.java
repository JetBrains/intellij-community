package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.get<caret>
    }
}
// EXIST: getData
// EXIST: getSize
