package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.xLongPrefix<caret>
    }
}
// EXIST: xLongPrefixGetter
// EXIST: xLongPrefixSetter
// NUMBER: 2