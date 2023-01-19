// WITH_STDLIB
// AFTER-WARNING: The expression is unused
fun foo() {
    val list = 1..4

    <caret>for (x in list) {
        x
    }
}