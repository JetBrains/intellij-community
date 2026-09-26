package a;

public class Testing {
    public static void test() {
        Target target = new Target();
        String s = target.<caret>
    }
}
// COMPLETION_TYPE: SMART
// EXIST: getLabel
// ABSENT: getCount
