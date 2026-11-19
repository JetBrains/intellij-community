// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Foo {
    fun <caret>len(x: Int) = hashCode() + x
}

val b8Unbound: (Foo, Int) -> Int = Foo::len
