// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion object {
        @JvmStatic
        fun f<caret>oo() = 0
    }
}