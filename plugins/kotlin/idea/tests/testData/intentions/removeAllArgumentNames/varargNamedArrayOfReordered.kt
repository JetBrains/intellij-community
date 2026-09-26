// INTENTION_TEXT: Remove all argument names
fun foo(s: String, vararg i: String) = s.length + i.size

fun main() {
    foo(<caret>i = arrayOf("1", "2", "3"), s = "a")
}