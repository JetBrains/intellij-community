// WITH_STDLIB
fun test(list: List<Int>) {
    val x: MutableList<Int> = list.<caret>filter { it > 1 }.flatMapIndexedTo(mutableListOf()) { _, i -> listOf(i) }
}