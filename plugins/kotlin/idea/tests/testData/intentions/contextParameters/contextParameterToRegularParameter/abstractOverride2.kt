// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

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
