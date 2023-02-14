// PROBLEM: none
// API_VERSION: 1.3
// WITH_STDLIB
fun main() {
    listOf(1, null, 2).<caret>sortedBy { it }.first()
}