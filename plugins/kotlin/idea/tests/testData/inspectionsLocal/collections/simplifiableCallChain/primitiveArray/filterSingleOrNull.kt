// WITH_STDLIB

fun test() {
    val array: IntArray = intArrayOf(0, 1, 2, 3)
    array.<caret>filter { it > 0 }.singleOrNull()
}