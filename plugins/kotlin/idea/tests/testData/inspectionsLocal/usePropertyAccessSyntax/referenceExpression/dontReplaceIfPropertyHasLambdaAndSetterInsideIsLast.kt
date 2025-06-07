// PROBLEM: none
// WITH_STDLIB

fun main() {
    val j = J()
    with(j) {
        val a = {
            println("Hello")
            <caret>setX(1)
        }
    }
}