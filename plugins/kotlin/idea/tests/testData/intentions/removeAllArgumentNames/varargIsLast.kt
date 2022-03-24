fun foo(s: String, t: String, vararg i: Int) = s.length + t.length + i.size

fun main() {
    foo(<caret>s = "a", t = "b", 1, 2, 3)
}