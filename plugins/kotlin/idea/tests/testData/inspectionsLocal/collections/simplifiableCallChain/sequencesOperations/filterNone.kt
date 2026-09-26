// WITH_STDLIB
fun main() {
    sequenceOf(1, 2, 3).<caret>filter { it > 1 }.none()
}