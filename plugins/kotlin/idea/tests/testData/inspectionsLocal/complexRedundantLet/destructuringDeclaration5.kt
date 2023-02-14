// WITH_STDLIB
// PROBLEM: none

fun test() {
    (1 to 2).let<caret> { (i, j) -> i.toLong() }
}