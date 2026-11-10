// COMPILER_ARGUMENTS: -Xcompanion-blocks

open class C4 {
    private class Hidden

    fun make<caret>Private(): Any = Hidden()
}
