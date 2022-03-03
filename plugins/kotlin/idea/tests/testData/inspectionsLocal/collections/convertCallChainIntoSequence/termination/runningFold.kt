// WITH_STDLIB

fun test(list: List<Int>) {
    list.<caret>filter { it > 1 }.runningFold(0) { acc, i -> acc + i }
}