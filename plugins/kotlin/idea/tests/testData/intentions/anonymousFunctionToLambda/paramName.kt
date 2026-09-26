// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'p' is never used
fun foo2(f: (Int) -> Unit) {
    f(1)
}

fun main(args: String) {
    foo2(<caret>fun(i) {
        val p = i
    })
}