// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: Modifiers should be placed directly before the relevant element
interface A {
    context(x: Int)
    fun test()
}

class B: A {
    override context(x: Int)
    @Deprecated("message")
    <caret>inline fun test() { }
}
