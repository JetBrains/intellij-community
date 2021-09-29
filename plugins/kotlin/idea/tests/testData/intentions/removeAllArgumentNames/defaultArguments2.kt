// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used
// AFTER-WARNING: Parameter 'd' is never used
fun foo(a: Int, b: Int, c: Int = 3, d: Int) {}

fun main() {
    foo(<caret>d = 4, c = 3, b = 2, a = 1)
}