// COMPILER_ARGUMENTS: -Xcompanion-blocks
// IS_APPLICABLE: false

class Foo {
    companion {
        fun <caret>bar() {
        }
    }
}
