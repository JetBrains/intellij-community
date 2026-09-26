// WITH_STDLIB
// AFTER-WARNING: Variable 'index' is never used
fun foo(bar: String) {
    for (<caret>a in bar) {
        print(a)
    }
}