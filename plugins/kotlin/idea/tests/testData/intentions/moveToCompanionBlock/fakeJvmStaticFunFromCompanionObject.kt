// COMPILER_ARGUMENTS: -Xcompanion-blocks

annotation class JvmStatic

class Foo {
    companion object {
        @JvmStatic
        fun f<caret>oo() = 0
    }
}