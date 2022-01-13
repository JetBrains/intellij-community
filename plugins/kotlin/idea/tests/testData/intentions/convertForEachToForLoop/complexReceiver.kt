// WITH_RUNTIME
// AFTER-WARNING: The expression is unused
fun main() {
    val x = 1..4

    x.reversed().forEach<caret> { it }
}