// WITH_STDLIB
fun main() {
    sequenceOf(1L, 2L).<caret>map { it * it }.sum()
}
