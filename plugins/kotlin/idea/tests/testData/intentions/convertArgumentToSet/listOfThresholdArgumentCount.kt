// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM: none

fun f(a: Iterable<Int>) {
    val b = a intersect <caret>listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
}
