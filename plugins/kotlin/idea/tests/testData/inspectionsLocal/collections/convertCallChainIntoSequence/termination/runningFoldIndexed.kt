// WITH_STDLIB

fun test(list: List<Int>) {
    list.<caret>filter { it > 1 }.runningFoldIndexed(0) { _, acc, i -> acc + i }
}