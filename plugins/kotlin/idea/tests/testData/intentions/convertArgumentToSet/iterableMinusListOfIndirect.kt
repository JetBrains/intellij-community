// WITH_STDLIB
// IS_APPLICABLE: false

fun <T : CharSequence> foo(a: Iterable<T>) {
    val b = listOf("a", "b", "c", "e")
    val c = a - <caret>b
}
