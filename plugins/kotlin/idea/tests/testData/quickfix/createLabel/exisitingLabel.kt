// "Create Label 'outer'@" "false"
// ERROR: 'break' and 'continue' are only allowed inside a loop
// WITH_STDLIB
// K2_AFTER_ERROR: NOT_A_LOOP_LABEL
// K2_ERROR: NOT_A_LOOP_LABEL
fun test() {
    val lists = listOf(null, listOf(1))

    for (list in lists)
        outer@ for (el in list ?: <caret> continue@outer) {
    }
}