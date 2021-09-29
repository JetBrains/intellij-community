// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'c' is never used
// AFTER-WARNING: Parameter 'd' is never used
fun foo(a: Int, b: Int = 2, c: Int, d: Int = 4) {}

fun main() {
    foo(<caret>a = 1, b = 2, c = 3)
}