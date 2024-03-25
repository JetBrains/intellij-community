// PROBLEM: none

fun main() {
    val j = J()
    with(j) {
        val a = {
            println("Hello")
            <caret>setX(1)
        }
    }
}