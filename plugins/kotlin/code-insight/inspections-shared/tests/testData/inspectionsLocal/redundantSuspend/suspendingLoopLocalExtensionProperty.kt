// PROBLEM: none

class SIterable {
}

<caret>suspend fun foo() {
    operator fun SIterable.iterator() = this
    suspend operator fun SIterable.hasNext(): Boolean = false
    suspend operator fun SIterable.next(): Int = 0

    val iterable = SIterable()
    for (x in iterable) {
    }
}