// AFTER-WARNING: Parameter 'fn' is never used
fun <T> foo(fn: (String) -> T) {}

fun test() {
    foo(<caret>fun(x: String) {
    })
}