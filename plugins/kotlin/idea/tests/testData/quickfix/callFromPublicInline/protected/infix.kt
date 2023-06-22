// "Make 'foo' public" "true"
open class A {
    protected infix fun foo(p: Int) {
    }

    inline fun call() {
        A() foo<caret> 8
    }
}
