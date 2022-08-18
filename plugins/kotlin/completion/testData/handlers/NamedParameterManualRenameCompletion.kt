fun foo(firstParam: Int, secondParam: Int) {}

fun main() {
    foo(first<caret> = 1, secondParam = 2)
}
