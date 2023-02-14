// WITH_STDLIB
// AFTER-WARNING: This class shouldn't be used in Kotlin. Use kotlin.Any instead.
// AFTER-WARNING: Variable 'a' is never used
// AFTER-WARNING: Variable 'index' is never used
fun foo(bar: Array<Object>) {
    for (<caret>a in bar) {

    }
}