// IS_APPLICABLE: false
fun foo(vararg i: Int, s: String, t: String) {}

fun main() {
    foo(<caret>i = intArrayOf(1, 2, 3), s = "", "")
}