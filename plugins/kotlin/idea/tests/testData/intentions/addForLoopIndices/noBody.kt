// WITH_STDLIB
// AFTER-WARNING: Variable 'a' is never used
// AFTER-WARNING: Variable 'index' is never used
// K2_ERROR: SYNTAX
// K2_AFTER_ERROR: SYNTAX
fun foo(bar: IntArray) {
    for (<caret>a in bar)
}