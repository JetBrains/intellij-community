// WITH_STDLIB

// should offer
// AFTER-WARNING: Variable 'c' is never used
val b = mutableListOf("a", "b", "c", "e")
fun <T : CharSequence> foo(a: Iterable<T>) {
    val c = a - <caret>b
}