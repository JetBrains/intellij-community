public class MyTest<X> {

    MyTest(X x) {}

    interface I<Z> {
        MyTest<Z> m(Z z);
    }

    static <Y> void test(I<Y> s, Y arg) {
        s.m(arg);
    }

    static {
        I<String> s = x -> new MyTest<String>(x);
    }
}