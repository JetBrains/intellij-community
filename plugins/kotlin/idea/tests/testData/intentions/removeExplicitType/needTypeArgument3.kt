// IGNORE_FIR
// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
fun <T> foo() {
    val x: <caret>Set<T & Any> = setOf()
}