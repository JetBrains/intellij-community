// WITH_STDLIB

fun test(list: List<Int>) {
    val x: List<Int> = list.<caret>filter { it > 1 }.scan(0) { acc, i -> acc + i }
}
