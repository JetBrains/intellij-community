// PROBLEM: none
// API_VERSION: 1.3
// WITH_STDLIB
fun main() {
    sequenceOf(1, null, 2).<caret>sortedBy { it }.last()
}