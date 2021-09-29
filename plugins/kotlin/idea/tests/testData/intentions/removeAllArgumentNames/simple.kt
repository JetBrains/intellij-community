// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used
fun foo(a: Int, b: Int, c: Int) {}

fun main() {
    foo(<caret>1, b = 2, c = 3)
}