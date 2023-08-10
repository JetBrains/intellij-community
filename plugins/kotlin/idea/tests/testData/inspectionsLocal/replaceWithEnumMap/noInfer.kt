// PROBLEM: none
// WITH_STDLIB

enum class E {
    A, B
}

fun main() {
    val map = <caret>hashMapOf<E, String>()
}
