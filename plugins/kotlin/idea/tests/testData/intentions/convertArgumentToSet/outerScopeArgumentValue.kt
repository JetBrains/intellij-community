// WITH_RUNTIME

// should offer
fun <T> f(a: Iterable<T>, b: Iterable<T>) {
    val c = a - <caret>b
}
f(listOf("a", "b"), listOf("b", "c"))
