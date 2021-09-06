// IS_APPLICABLE: false
fun foo(a: Int, b: Int, c: Int) {}

fun main() {
    foo(<caret>a = 1, c = 3, b = 2)
}