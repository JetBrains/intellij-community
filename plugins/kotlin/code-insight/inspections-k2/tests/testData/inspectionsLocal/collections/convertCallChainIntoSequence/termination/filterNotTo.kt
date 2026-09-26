// WITH_STDLIB

fun test(list: List<Int>) {
    val filterNotTo: MutableList<Int> = list.<caret>filter { it > 1 }.filterNotTo(mutableListOf()) { true }
}