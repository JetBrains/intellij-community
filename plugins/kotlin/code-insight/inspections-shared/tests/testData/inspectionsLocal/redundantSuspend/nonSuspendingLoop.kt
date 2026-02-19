class Iterable {
    operator fun iterator() = this
    operator fun hasNext(): Boolean = false
    operator fun next(): Int = 0
}

<caret>suspend fun foo() {
    val iterable = Iterable()
    for (x in iterable) {
    }
}