// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    fun <caret>len(x: Int): Int = if (x > 0) listOf(x - 1).map(::len).sum() else 0
}
