package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.alpha<caret>
    }
}
// EXIST: alphaName
// ABSENT: betaName
