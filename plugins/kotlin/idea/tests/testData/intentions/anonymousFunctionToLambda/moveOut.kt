// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Variable 'p' is never used
fun foo(f: () -> Unit, i: Int) {
    f()
}

fun main(args: String) {
    foo(<caret>fun() {
        val p = 1
    }, 1)
}