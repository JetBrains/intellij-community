class Base {
    void foo() {
    }
}

public class <caret>QualifiedThis extends Base {
    void foo() {
    }

    class Inner {
        void bar() {
            QualifiedThis.this.foo();
        }
    }
}
