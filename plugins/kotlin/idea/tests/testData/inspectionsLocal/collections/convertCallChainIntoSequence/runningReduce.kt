// WITH_STDLIB

fun test(list: List<Int>) {
    val x: List<Int> = list.<caret>filter { it > 1 }.runningReduce { acc, i -> acc + i }
}