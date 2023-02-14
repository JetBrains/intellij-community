// PROBLEM: none
// WITH_STDLIB

fun test() {
    val list = mutableListOf(1, 2, 3)
    list[1] = <caret>list[1]
}