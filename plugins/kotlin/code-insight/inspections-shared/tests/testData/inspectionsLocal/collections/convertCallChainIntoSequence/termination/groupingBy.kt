// WITH_STDLIB

fun test(list: List<Int>) {
    val groupingBy: Grouping<Int, Int> = list.<caret>filter { it > 1 }.groupingBy { it }
}