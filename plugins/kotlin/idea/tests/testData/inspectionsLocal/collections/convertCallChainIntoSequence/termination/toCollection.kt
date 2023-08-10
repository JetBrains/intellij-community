// WITH_STDLIB

fun test(list: List<Int>) {
    val toCollection: MutableList<Int> = list.<caret>filter { it > 1 }.toCollection(mutableListOf())
}