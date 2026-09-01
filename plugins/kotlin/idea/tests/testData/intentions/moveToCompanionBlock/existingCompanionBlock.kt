// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion {
        fun existing() {
        }
    }

    fun <caret>bar() {
    }
}