// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'x' is never used
fun `super`(x: Int): Int = TODO()

fun main(args: Array<String>) {
    listOf(1).map { <caret>`super`(it) }
}