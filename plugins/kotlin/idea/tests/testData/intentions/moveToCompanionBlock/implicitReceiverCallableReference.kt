// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: Callable reference won't be processed: ::extension
// SHOULD_FAIL_WITH: Callable reference won't be processed: ::other

class Foo {
    fun other(x: Int) = x

    fun <caret>len(xs: List<Int>) = xs.map(::other) + xs.map(::extension)
}

fun Foo.extension(x: Int) = x
