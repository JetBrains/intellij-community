// WITH_STDLIB

fun test(list: List<Int>) {
    val toMutableSet = list.<caret>filter { it > 1 }.toMutableSet()
}