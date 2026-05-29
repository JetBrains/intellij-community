package a;

public class Testing {
    static class ConcreteProcessor implements Processor {}

    public static void test() {
        Processor p = new ConcreteProcessor();
        p.<caret>
    }
}
// EXIST: process