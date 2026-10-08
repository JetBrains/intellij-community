// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Foo {
    companion object {
        @JvmStatic val b<caret>ar: Int get() = 0
    }
}