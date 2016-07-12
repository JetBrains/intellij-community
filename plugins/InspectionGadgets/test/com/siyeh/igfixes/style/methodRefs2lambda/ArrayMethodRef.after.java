public class Foo {
    static void foo() {
        Cln j = (ints) -> ints.clone();
    }

    interface Cln {
        Object _(int[] p);
    }
}