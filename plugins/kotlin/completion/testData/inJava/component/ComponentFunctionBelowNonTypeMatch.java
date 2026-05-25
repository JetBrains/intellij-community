package a;

public class Testing {
    public static String test() {
        Entry entry = new Entry("hello");
        return entry.<caret>
    }
}

// WITH_ORDER
// EXIST: getSize
// EXIST: component1
