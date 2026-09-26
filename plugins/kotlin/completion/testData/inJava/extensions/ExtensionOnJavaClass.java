package a;

class JavaBean {}

public class Testing {
    public static void test() {
        JavaBean bean = new JavaBean();
        bean.<caret>
    }
}
// EXIST: process
