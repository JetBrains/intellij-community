// WITH_STDLIB
fun foo(vararg i: Int, s: String, t: String) = i.size + s.length + t.length

fun main() {
    foo(<caret>i = listOf(1, 2, 3).toIntArray(), s = "", t = "")
}