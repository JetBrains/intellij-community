// WITH_STDLIB

data class A(val <caret>a: IntArray) {
    fun equals(other: Any?, excessive: Any?): Boolean {
        return true
    }

    override fun hashCode(): Int {
        return a.contentHashCode()
    }
}
