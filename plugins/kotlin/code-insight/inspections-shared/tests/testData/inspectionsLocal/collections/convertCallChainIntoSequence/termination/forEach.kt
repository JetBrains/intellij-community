// PROBLEM: none
// WITH_STDLIB

fun test(list: List<Int>) {
    val forEach: Unit = list.<caret>filter { it > 1 }.forEach { }
}