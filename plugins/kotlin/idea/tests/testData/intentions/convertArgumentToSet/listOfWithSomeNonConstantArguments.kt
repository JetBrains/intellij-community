// WITH_STDLIB
// AFTER-WARNING: Variable 'c' is never used

fun bar(): Int = TODO()

fun foo(a: Iterable<Int>) {
    val b = listOf(1, bar(), 3)
    val c = a - <caret>b
}
