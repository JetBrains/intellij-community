// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// SHOULD_FAIL_WITH: Function foo() is overridden by declaration(s) in a subclass

open class A {
    open fun <caret>foo() = 0
}

class B : A() {
    override fun foo() = 1
}
