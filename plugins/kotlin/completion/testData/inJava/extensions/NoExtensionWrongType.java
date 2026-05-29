package a;

public class Testing {
    public static void test() {
        TypeB b = new TypeB();
        b.<caret>
    }
}
// ABSENT: extensionForA