// AFTER-WARNING: Parameter 'fn' is never used
// AFTER-WARNING: Parameter 'p' is never used
fun foo(p: String) {}

fun <T> bar(fn: (T) -> Unit) {}

fun test() {
    bar<String>(<caret>fun(x: String) {
        foo(x)
    })
}