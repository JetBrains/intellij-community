// PROBLEM: none

class OtherIterator {
    suspend operator fun hasNext(): Boolean = false
    suspend operator fun next(): Int = 0
}

class SIterable {
    operator fun iterator() = OtherIterator()
}

<caret>suspend fun foo() {
    val iterable = SIterable()
    for (x in iterable) {
    }
}