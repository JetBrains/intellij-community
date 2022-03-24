// AFTER-WARNING: Parameter 'fn' is never used
// AFTER-WARNING: Parameter 'p' is never used
// AFTER-WARNING: Parameter 'q' is never used
// AFTER-WARNING: Parameter 'r' is never used
fun foo(p: String, q: Int, r: Long) {}

fun <T, U> bar(fn: (T, Int, U) -> Unit) {}

fun test() {
    bar(<caret>fun(x: String, y: Int, z: Long) {
        foo(x, y, z)
    })
}