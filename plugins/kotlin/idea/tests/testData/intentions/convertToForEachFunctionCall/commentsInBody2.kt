// WITH_RUNTIME
fun foo() {
    val list = 1..4

    <caret>for (x in list) {
        // comment
// AFTER-WARNING: Variable 'v' is never used
        var v = x + 1
    }
}