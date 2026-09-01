// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    private fun <caret>helper() {
    }

    fun bar() {
        helper()
    }
}