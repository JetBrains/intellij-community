class SIterable {
}

<caret>suspend fun foo() {
    operator fun SIterable.iterator() = this
    operator fun SIterable.hasNext(): Boolean = false
    operator fun SIterable.next(): Int = 0

    val iterable = SIterable()
    for (x in iterable) {
    }
}