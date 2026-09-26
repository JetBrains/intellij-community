package a;

public class Testing {
    public static void test() {
        Derived d = new Derived();
        d.<caret>
    }
}
// EXIST: baseExt