// COMPILER_ARGUMENTS: -Xcontext-parameters
open class Base {
    open fun foo(x: Int) {}
}

class Derived : Base() {
    override fun foo(<caret>x: Int) {}
}