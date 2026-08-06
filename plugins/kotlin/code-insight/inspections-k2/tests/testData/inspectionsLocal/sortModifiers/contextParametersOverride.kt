// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: Modifiers should be placed directly before the relevant element
interface A {
    context(x: Int)
    fun test()
}

class B: A {
    override<caret> context(x: Int)
    fun test() { }
}
