// WITH_STDLIB
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
fun foo() {
    <caret>for (x in 1..10) {
    }
}