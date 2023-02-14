// WITH_STDLIB

fun test(list: List<Int>) {
    val single: Int = list.<caret>filter { it > 1 }.single()
}