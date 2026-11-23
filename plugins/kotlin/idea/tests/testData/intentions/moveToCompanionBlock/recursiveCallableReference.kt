// COMPILER_ARGUMENTS: -Xcompanion-blocks
// SHOULD_FAIL_WITH: Callable reference won't be processed: this::len

class Foo {
    fun <caret>len(x: Int): Int = if (x > 0) listOf(x - 1).map(this::len).sum() else hashCode()
}
