fun foo(s: String, t: String, vararg i: Int) {}

fun main() {
    foo(<caret>s = "a", t = "b", 1, 2, 3)
}