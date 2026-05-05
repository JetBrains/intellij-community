package a;

public class Testing {
    static void process(String label) {}

    public static void test() {
        Target target = new Target();
        process(target.<caret>)
    }
}
// COMPLETION_TYPE: SMART
// EXIST: toLabel
// ABSENT: toNumber
