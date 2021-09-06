// IS_APPLICABLE: false
fun foo(vararg i: Int, s: String, t: String) {}

fun main() {
    foo(<caret>1, 2, 3, s = "", t = "")
}