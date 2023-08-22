// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 'p1' is never used
// AFTER-WARNING: Variable 'p2' is never used
fun foo(f: () -> Unit) {
    f()
}

fun main(args: String) {
    foo(<caret>fun() {
        val p1 = 1
        val p2 = 1
    })
}