// WITH_STDLIB
// AFTER-WARNING: The expression is unused

infix fun Int.upTo(other: Int) = this.rangeTo(other)

fun main() {
    (1 upTo 2).<caret>forEach { x -> x }
}