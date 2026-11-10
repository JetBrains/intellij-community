// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion {
        fun helper(x: Int) = x
    }

    fun moved<caret>(xs: List<Int>) = xs.map(::helper)
}
