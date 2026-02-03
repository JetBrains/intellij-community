// WITH_STDLIB
// AFTER-WARNING: Variable 'c' is never used

fun <T : CharSequence> foo(a: Iterable<T>) {
    val b = arrayListOf("a", "b", "c", "e")
    val c = a - <caret>b
}
