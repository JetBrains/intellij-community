// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none

// shouldn't offer
val b = listOf("a", "b", "c", "e")
fun <T : CharSequence> foo(a: Iterable<T>) {
    val c = a - <caret>b
}
