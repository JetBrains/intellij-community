// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// SHOULD_FAIL_WITH: Callable reference won't be processed: foo::len

class Foo {
    fun <caret>len(x: Int) = hashCode() + x
}

fun boundParam(foo: Foo): (Int) -> Int = foo::len
