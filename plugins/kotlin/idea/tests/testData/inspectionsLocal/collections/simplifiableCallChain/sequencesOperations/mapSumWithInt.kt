// WITH_STDLIB
fun main() {
    sequenceOf(1, 2).<caret>map { it * it }.sum()
}
