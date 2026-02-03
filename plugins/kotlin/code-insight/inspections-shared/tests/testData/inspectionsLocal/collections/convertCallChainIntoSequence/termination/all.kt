// WITH_STDLIB

fun test(list: List<Int>) {
    val all: Boolean = list.<caret>filter { it > 1 }.all { true }
}