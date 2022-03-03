// WITH_STDLIB
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
fun foo() {
    <caret>for (x /* current */ in 1..10/* from 1 to 10 */) { }
}