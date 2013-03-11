public class Foo {
    static void foo() {
        Cln j = (p) -> p.clone();
    }

    interface Cln {
        Object _(int[] p);
    }
}