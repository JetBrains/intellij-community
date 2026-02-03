// WITH_STDLIB

fun test(list: List<Int>) {
    val lastOrNull = list.<caret>filter { it > 1 }.lastOrNull()
}