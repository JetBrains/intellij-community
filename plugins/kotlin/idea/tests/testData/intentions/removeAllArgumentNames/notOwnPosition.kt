fun foo(a: Int, b: Int, c: Int, d: Int) = a + b + c + d

fun main() {
    foo(<caret>a = 1, d = 4, c = 3, b = 2)
}