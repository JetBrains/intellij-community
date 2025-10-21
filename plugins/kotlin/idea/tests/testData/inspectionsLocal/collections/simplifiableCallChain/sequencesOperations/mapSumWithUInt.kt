// WITH_STDLIB
fun main() {
    sequenceOf(1u, 2u).<caret>map { it * it }.sum()
}
