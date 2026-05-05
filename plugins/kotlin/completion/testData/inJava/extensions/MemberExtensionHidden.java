package a;

public class Testing {
    public static void test() {
        Target t = new Target();
        t.<caret>
    }
}
// ABSENT: help
// ABSENT: assist
