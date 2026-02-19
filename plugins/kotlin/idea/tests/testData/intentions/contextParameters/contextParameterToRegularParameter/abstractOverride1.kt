// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: Class 'Derived' is not abstract and does not implement abstract base class member:<br>context(c1: Int) fun foo(): Unit
// IGNORE_K2

abstract class Base {
    context(c1: Int)
    abstract fun foo()
}

class Derived : Base() {
    context(c1: Int<caret>)
    override fun foo() {}

    context(c1: Int)
    fun bar() {
        foo()
    }
}
