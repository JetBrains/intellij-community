// WITH_STDLIB

fun test(list: List<Int>) {
    val average: Double = list.<caret>filter { it > 1 }.average()
}