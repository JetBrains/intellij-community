// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none

fun <T : CharSequence> foo(a: Iterable<T>) {
    val b = arrayOf("a", "b", "c", "e")
    val c = a - <caret>b
}
