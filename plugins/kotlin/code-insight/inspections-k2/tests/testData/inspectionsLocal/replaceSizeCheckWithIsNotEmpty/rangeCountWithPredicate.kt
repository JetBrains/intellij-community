// PROBLEM: none
// WITH_STDLIB

fun test() {
    val range = 1..9
    if (<caret>range.count { it == 1 } != 0) {
        println()
    }
}