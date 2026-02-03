// WITH_STDLIB
// AFTER-WARNING: Variable 'a' is never used
// AFTER-WARNING: Variable 'index' is never used
fun foo(bar: IntArray) {
    for (<caret>a in bar)
}