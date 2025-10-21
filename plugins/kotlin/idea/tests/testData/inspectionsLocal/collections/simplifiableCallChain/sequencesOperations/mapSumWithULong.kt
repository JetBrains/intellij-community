// WITH_STDLIB
fun main() {
    sequenceOf(1uL, 2uL).<caret>map { it * it }.sum()
}
