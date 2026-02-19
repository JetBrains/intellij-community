// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K2

abstract class Base {
    context(c1: Int, c2: String<caret>)
    abstract fun foo(p1: Double)
}

class Derived : Base() {
    context(c1: Int, c2: String)
    override fun foo(p1: Double) {}

    context(c1: String, c2: Int)
    fun bar() {
        foo(0.0)
    }
}
