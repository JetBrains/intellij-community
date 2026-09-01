// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion object {
        val v<caret> = 1

        fun bar() {
        }
    }
}