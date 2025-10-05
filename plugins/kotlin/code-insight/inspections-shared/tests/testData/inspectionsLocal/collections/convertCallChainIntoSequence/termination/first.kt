// WITH_STDLIB

fun test(list: List<Int>) {
    val first: Int = list.<caret>filter { it > 1 }.first()
}