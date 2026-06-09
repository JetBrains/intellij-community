// WITH_STDLIB
// AFTER-WARNING: Name shadowed: i
// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
fun foo() {
    val list = 1..4
    val i = 0

    <caret>for (i in list)
        i
    i
}