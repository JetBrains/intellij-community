package pkg;

import java.util.function.Consumer;

public interface TestLambda {
    public static void main(String[] args) {
        Consumer<String> a = b -> System.out.println(b);
        Consumer<String> a2 = System.out::println;
        Consumer<String> a3 = TestLambda::test;
    }


    static void test(String a) {
        System.out.println(a);
    }
}