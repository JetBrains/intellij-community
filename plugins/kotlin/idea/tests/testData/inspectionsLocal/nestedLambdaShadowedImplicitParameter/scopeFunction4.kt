// PROBLEM: none
// WITH_STDLIB

fun main() {
    listOf(42).map {
        it.takeUnless {
            <caret>it == 42
        }
    }
}