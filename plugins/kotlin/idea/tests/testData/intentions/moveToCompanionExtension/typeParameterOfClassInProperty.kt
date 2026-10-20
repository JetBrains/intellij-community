// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// SHOULD_FAIL_WITH: property first references type parameters of the containing class

class A<T>(val value: T) {
    val <caret>first: T get() = TODO()
}
