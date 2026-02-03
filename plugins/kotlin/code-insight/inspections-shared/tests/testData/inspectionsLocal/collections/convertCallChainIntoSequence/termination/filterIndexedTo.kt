// WITH_STDLIB

fun test(list: List<Int>) {
    val filterIndexedTo: MutableList<Int> = list.<caret>filter { it > 1 }.filterIndexedTo(mutableListOf()) { index, it -> true }
}
