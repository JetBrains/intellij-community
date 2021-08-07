// WITH_RUNTIME
fun main() {
    listOf(1L, 2L).<caret>map { it * it }.sum()
}
