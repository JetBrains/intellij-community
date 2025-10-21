// WITH_STDLIB

fun test(list: List<Int>) {
    val mapTo: MutableList<Int> = list.<caret>filter { it > 1 }.mapTo(mutableListOf()) { it }
}