fun foo(b: Boolean) {
    <caret>if (b) 1 // 1
// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused
    else 2
}