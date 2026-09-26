// INTENTION_TEXT: Remove all argument names
fun foo(a: Int, b: Int, c: Int = 3, d: Int) = a + b + c + d

fun main() {
    foo(<caret>d = 4, c = 3, b = 2, a = 1)
}