// PROBLEM: none
// WITH_STDLIB
// DISABLE-ERRORS
fun test() {
    intArrayOf(1, 2).flatMap<caret> { it }
}