class Base {
    def foo() {
        foo(123)
    }

    def foo(int anObject){}
}

class Inh extends Base {
    def foo() {
        foo(123)
    }

    def foo(int anObject) {print anObject }
}