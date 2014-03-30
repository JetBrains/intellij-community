public class A {
}

class B extends A {
    final String <caret>f;
    final String foo;

    B(String fi, String foo) {
        this.foo = foo;
        if (fi == this.foo) {
            f = foo;
        } else {
            f = "";
        }
    }

    B(String f) {
        this.f = f;
        foo = "";
    }
}
