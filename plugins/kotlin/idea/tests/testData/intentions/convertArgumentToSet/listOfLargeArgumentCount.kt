// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used

fun f(a: Iterable<Int>) {
    val b = a intersect <caret>listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
}
