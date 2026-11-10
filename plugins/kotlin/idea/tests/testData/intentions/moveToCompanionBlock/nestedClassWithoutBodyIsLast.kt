// COMPILER_ARGUMENTS: -Xcompanion-blocks

open class C4 {
    fun make<caret>Private(): Any = Hidden()

    private class Hidden
}
