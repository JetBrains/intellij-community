// WITH_STDLIB

fun test(list: List<Int>) {
    val contains: Boolean = list.<caret>filter { it > 1 }.contains(1)
}