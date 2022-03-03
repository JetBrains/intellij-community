// PROBLEM: none
// WITH_STDLIB

fun main() {
    listOf(42).map {
        it.takeIf {
            <caret>it == 42
        }
    }
}