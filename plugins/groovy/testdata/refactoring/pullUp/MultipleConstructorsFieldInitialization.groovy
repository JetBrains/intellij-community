public class A {
}

class B extends A {
    final String <caret>f;
    final String foo;

    B(String fi, String foo) {
        this.foo = foo;
        f = "";
    }

    B(String foo) {
        this.foo = foo;
        f = "";
    }
}
