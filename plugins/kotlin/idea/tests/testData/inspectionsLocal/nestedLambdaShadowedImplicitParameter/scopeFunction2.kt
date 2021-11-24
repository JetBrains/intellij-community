// PROBLEM: none
// WITH_STDLIB

fun main() {
    listOf(42).map {
        it.let {
            <caret>it == 42
        }
    }
}