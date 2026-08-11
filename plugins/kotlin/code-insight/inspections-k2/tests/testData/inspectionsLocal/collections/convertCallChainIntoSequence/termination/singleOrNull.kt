// WITH_STDLIB

fun test(list: List<Int>) {
    val singleOrNull: Int? = list.<caret>filter { it > 1 }.singleOrNull()
}