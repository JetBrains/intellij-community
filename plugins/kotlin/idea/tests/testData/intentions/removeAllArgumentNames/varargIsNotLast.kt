fun foo(vararg i: Int, s: String, t: String) = i.size + s.length + t.length

fun main() {
    foo(<caret>i = intArrayOf(1, 2, 3), s = "", t = "")
}