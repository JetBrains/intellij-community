public class Foo {
    public void test() {
        Object i = null;
        long[][] avg = collect((i1) -> new long[i1][]);
    }

    interface P<T> {
        T _(int i);
    }

    <T> T collect(P<T> h) {
        return null;
    }
}