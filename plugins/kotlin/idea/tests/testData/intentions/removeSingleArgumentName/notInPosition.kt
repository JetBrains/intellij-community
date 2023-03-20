fun foo(a: Int, b: Int) = a + b

fun main() {
    foo(b = 3, <caret>a = 1)
}
