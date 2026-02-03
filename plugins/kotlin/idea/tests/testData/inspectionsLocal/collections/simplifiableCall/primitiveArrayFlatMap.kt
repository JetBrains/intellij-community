// PROBLEM: none
// WITH_STDLIB
// DISABLE_ERRORS
fun test() {
    intArrayOf(1, 2).flatMap<caret> { it }
}