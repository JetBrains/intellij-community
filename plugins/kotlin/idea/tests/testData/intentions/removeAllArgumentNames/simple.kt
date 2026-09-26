// INTENTION_TEXT: Remove all argument names
fun foo(a: Int, b: Int, c: Int) = a + b + c

fun main() {
    foo(<caret>1, b = 2, c = 3)
}