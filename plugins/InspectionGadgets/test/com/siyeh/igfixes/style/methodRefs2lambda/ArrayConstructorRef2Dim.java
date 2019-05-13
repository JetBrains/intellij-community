public class Foo {
    static void foo() {
        Ar<String> a = Str<caret>ing[][]::new;
    }

    interface Ar<T> {
        T[][] jjj(int p);
    }
}



