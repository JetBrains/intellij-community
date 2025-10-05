package test;

class ActionTest {
    void test(Action action) {
        action.execute(42);
    }

    void testLambda() {
        test(it -> System.out.println("it: " + it));
    }

    public static void main(String[] args) {
    }
}