fun foo(firstParam: Int, secondParam: Int) {}

fun main() {
    foo(first<caret>secondParam = 2)
}
