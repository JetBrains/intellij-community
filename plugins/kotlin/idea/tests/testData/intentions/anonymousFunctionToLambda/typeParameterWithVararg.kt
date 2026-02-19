// AFTER-WARNING: Parameter 'fn' is never used
// AFTER-WARNING: Parameter 'p' is never used
fun foo(p: String) {}

fun <T> bar(vararg fn: (T) -> Unit) {}

fun test() {
    bar(<caret>fun(x: String) {
        foo(x)
    })
}