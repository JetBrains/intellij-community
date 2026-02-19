// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
// IGNORE_K1

fun foo(x: Int) {
    <caret>if (x == 42) {
        "42"
    }
    if (x == 239) {
        "239"
    }
}