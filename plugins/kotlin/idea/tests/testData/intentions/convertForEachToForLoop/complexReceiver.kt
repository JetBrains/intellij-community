// WITH_STDLIB
// AFTER-WARNING: The expression is unused
fun main() {
    val x = 1..4

    x.reversed().forEach<caret> { it }
}