// WITH_RUNTIME
// AFTER-WARNING: Variable 'index' is never used
fun foo(bar: Sequence<String>) {
    for (<caret>a in bar)
        print(a)
}