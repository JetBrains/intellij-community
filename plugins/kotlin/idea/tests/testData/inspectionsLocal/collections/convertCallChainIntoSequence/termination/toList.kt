// WITH_STDLIB

fun test(list: List<Int>) {
    val toList: List<Int> = list.<caret>filter { it > 1 }.toList()
}