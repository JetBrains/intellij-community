// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused


fun foo(x: Int) {
    <caret>if (x == 42) {
        "42"
    }
    if (x == 239) {
        "239"
    }
}