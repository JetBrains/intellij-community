// COMPILER_ARGUMENTS: -Xcompanion-blocks
// K2_AFTER_ERROR: CONFLICTING_OVERLOADS
// K2_AFTER_ERROR: CONFLICTING_OVERLOADS

class Foo {
    fun <caret>f1(n: Int) {}
    companion {
        fun f1(n: Int) {}
    }
}