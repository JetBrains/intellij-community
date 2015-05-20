package foo;

public class FooClass {
    public int method1() {
        return 1;
    }

    public int method2(boolean value) {
        return value ? 2 : 3;  // will be a partially covered line
    }
}
