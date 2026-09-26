// WITH_STDLIB
// AFTER-WARNING: Variable 'v' is never used
fun foo() {
    val list = 1..4

    <caret>for (x in list) {
        // comment
        var v = x + 1
    }
}