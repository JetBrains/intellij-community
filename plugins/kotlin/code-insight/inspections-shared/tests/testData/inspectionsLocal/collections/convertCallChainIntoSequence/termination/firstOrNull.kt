// WITH_STDLIB

fun test(list: List<Int>) {
    val firstOrNull: Int? = list.<caret>filter { it > 1 }.firstOrNull()
}