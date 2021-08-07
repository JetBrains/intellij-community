// WITH_RUNTIME
fun main() {
    listOf(1u, 2u).<caret>map { it * it }.sum()
}
