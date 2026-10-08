// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    companion object {
        @JvmStatic var b<caret>ar: Int = 0
    }
}