// COMPILER_ARGUMENTS: -Xcontext-parameters

open class Base {
    context(s: String)
    open val value: Int
        get() = s.length
}

class Derived : Base() {
    context(<caret>s: String)
    override val value: Int
        get() = s.length
}
