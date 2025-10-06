// WITH_STDLIB
fun main() {
    sequenceOf(1.0, 2.0).<caret>map { it * it }.sum()
}
