// PROBLEM: none
// WITH_STDLIB

fun test(list: List<Int>) {
    val forEachIndexed: Unit = list.<caret>filter { it > 1 }.forEachIndexed { index, i -> }
}