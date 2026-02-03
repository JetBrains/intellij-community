// WITH_STDLIB

fun test(list: List<Int>) {
    list.<caret>filter { it > 1 }.reduceOrNull { acc, i -> acc + i }
}