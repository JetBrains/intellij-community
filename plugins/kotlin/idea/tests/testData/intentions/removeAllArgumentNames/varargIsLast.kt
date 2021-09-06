// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 's' is never used
// AFTER-WARNING: Parameter 't' is never used
fun foo(s: String, t: String, vararg i: Int) {}

fun main() {
    foo(<caret>s = "a", t = "b", 1, 2, 3)
}