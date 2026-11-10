// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Foo {
    fun moved<caret>(xs: List<Int>) = xs.map(::helper)
}

companion fun Foo.helper(x: Int) = x