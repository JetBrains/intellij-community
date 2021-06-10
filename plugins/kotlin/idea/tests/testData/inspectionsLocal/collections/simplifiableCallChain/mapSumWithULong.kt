// WITH_RUNTIME
fun main() {
    listOf(1uL, 2uL).<caret>map { it * it }.sum()
}
