// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Foo {
    fun <caret>f1(n: Int) {}
    companion {
        fun f1(n: String) {}
    }
}