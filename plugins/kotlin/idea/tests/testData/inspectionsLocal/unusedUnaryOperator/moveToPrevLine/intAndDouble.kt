// FIX: Move unary operator to previous line
fun foo() = 1.1

fun main() {
    val a = 2 - foo() // comment
    <caret>- 4
}