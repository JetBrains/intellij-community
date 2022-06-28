// INTENTION_TEXT: "Replace size check with '!isEmpty'"
// WITH_STDLIB
fun main() {
    val range = 1 until 2
    if (<caret>range.count() != 0) {
        println()
    }
}