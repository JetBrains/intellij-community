// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none

fun <T : CharSequence> foo(a: Iterable<T>) {
    val c = a - <caret>kotlin.collections.listOf("a", "b", "c", "e")
}
