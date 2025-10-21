// WITH_STDLIB

fun test(list: List<Int>) {
    val groupBy = list.<caret>filter { it > 1 }.groupBy { it }
}