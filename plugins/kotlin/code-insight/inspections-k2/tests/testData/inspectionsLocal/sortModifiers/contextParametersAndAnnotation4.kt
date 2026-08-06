// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: Modifiers should be placed directly before the relevant element
annotation class Ann

class B {
    @Ann
    private context(x: Int)
    @Deprecated("alas")
    inline<caret> fun test() { }
}
