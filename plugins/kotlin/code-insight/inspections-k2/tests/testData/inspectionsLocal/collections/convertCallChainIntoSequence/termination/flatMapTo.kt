// WITH_STDLIB
fun test(list: List<Int>) {
    val x: MutableList<Int> = list.<caret>filter { it > 1 }.flatMapTo(mutableListOf()) { listOf(it) }
}