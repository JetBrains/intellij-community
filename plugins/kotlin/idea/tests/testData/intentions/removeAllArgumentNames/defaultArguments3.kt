fun foo(a: Int, b: Int = 2, c: Int, d: Int = 4) = a + b + c + d

fun main() {
    foo(<caret>a = 1, b = 2, c = 3)
}