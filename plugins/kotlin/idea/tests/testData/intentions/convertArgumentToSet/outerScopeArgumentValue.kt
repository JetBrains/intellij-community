// WITH_STDLIB

// should offer
// AFTER-WARNING: Variable 'c' is never used
fun <T> f(a: Iterable<T>, b: Iterable<T>) {
    val c = a - <caret>b
}
f(listOf("a", "b"), listOf("b", "c"))
