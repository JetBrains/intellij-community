// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'p' is never used
fun foo(f: () -> Unit) {
    f()
}

fun main(args: String) {
    foo(fun<caret>() {
        val p = 1
    })
}