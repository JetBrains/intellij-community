// WITH_STDLIB
// IS_APPLICABLE: false
// PROBLEM none

fun foo(a: Iterable<Int>) {
    val x: Int = 5
    val b = listOf(1, x + 1, 3)
    val c = a - <caret>b
}

// IGNORE_K2