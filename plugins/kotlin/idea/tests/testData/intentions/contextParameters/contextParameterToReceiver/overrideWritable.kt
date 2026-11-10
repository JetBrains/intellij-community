// COMPILER_ARGUMENTS: -Xcontext-parameters

open class Base {
    context(s: String)
    open fun foo() {}
}

class Derived : Base() {
    context(<caret>s: String)
    override fun foo() {}
}
