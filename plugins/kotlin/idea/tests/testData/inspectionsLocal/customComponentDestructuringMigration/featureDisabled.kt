// PROBLEM: none
// WITH_STDLIB
// No name-based destructuring feature enabled

fun test() {
    val (x<caret>, y) = listOf(1, 2)
}