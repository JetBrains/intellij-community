// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Foo {
    companion object {
        @JvmStatic
        fun f<caret>oo() = 0
    }
}