// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion {
        fun helper(x: Int): Int = x
    }

    class Bar {
        fun m<caret>oved(xs: List<Int>) = xs.map(::helper)
    }
}