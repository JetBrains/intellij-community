// WITH_STDLIB

fun test(list: List<Int>) {
    val count: Int = list.<caret>filter { it > 1 }.count()
}