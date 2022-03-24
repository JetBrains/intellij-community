// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
fun foo(f: (x: Int) -> Unit) {}

fun bar(x: Int, y: Int = 42) {}

fun test() {
    foo <caret>{ bar(it) }
}
