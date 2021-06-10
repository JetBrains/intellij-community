// PROBLEM: none
// WITH_RUNTIME
fun main() {
    listOf(0.1f, 0.2f).<caret>map { it * it }.sum()
}
