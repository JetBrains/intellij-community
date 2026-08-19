package foo;

public class CoverageApp {
    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) {
            Thread.sleep(Integer.parseInt(args[0]));
        }
        new FooClass().method1();
    }
}
