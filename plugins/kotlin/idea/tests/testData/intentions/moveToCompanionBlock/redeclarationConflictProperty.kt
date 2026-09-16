// COMPILER_ARGUMENTS: -Xcompanion-blocks
// K2_AFTER_ERROR: REDECLARATION
// K2_AFTER_ERROR: REDECLARATION

class Foo {
    val i<caret>: Int = 1
    companion {
        val i: Int = 2
    }
}