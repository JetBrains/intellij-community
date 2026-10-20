// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// IS_APPLICABLE: false

open class A {
    protected fun <caret>foo() = 1
}
