// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion object {
        fun f<caret>oo() {
        }

        fun bar() {
        }
    }
}