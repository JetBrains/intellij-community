// WITH_STDLIB

data class A(val <caret>a: IntArray) {
    override fun hashCode(): Int {
        return a.contentHashCode()
    }
}
