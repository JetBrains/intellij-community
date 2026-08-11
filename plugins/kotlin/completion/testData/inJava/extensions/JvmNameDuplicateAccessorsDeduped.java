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

// IGNORE_K2
// Note: This test fails due to KT-87399, unmute once the issue is fixed
