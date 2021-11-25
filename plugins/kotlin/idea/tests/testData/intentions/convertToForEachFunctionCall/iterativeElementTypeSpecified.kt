// WITH_STDLIB
// AFTER-WARNING: The expression is unused
fun main() {
    val list = 1..4

    <caret>for (x: Int in list) {
        x
    }
}