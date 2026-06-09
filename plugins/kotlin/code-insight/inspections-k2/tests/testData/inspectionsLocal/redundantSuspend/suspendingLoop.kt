// PROBLEM: none

class SIterable {
    operator fun iterator() = this
    suspend operator fun hasNext(): Boolean = false
    suspend operator fun next(): Int = 0
}

<caret>suspend fun foo() {
    val iterable = SIterable()
    for (x in iterable) {
    }
}