// WITH_RUNTIME
fun main() {
    listOf(1, 2, 3).<caret>filter { it > 1 }.count()
}