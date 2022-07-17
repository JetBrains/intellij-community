// WITH_STDLIB

fun test(list: List<Int>) {
    val toMutableList = list.<caret>filter { it > 1 }.toMutableList()
}