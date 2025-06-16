// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

abstract class Base {
    abstract fun foo(c1: Int)
}

class Derived : Base() {
    override fun foo(<caret>c1: Int) {}

    context(c1: Int)
    fun bar() {
        foo(c1)
    }
}