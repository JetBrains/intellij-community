// PROBLEM: none
// WITH_STDLIB

fun main() {
    listOf(42).map {
        it.also {
            <caret>it == 42
        }
    }
}