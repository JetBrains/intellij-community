// PROBLEM: none
// WITH_STDLIB
fun foo(list: List<Int>): Int? =
    list.<caret>asSequence()
        .runningFold(0) { x, y -> x + y }
        .foo()
        .lastOrNull()

fun Sequence<Int>.foo(): Sequence<Int> = map { it + 1 }