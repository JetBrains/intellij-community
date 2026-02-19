// "Create Label 'outer'@" "false"
// ERROR: 'break' and 'continue' are only allowed inside a loop
// K2_AFTER_ERROR: Label does not denote a reachable loop.
// WITH_STDLIB
fun test() {
    val lists = listOf(null, listOf(1))

    for (list in lists)
        outer@ for (el in list ?: <caret> continue@outer) {
    }
}