// COMPILER_ARGUMENTS: -Xcontext-parameters

abstract class Base {
    context(c1: Int<caret>)
    abstract fun foo()
}

class Derived : Base() {
    context(c1: Int)
    override fun foo() {}

    context(c1: Int)
    fun bar() {
        foo()
    }
}
