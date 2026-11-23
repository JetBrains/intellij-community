// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// Issue: KT-87036

class Foo {
    companion object {
        fun <caret>len(x: Int) = x
    }
}

val companionReference: (Int) -> Int = Foo::len
