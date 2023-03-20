// PROBLEM: none
// WITH_STDLIB

fun test(list: List<String>) {
    listOf(1, 2, 3).<caret>count { it == 2 } != 0
}