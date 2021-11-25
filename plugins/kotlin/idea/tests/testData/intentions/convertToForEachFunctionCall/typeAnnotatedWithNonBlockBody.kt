// WITH_STDLIB
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
// AFTER-WARNING: The expression is unused
fun main() {
    val list = 1..4

    <caret>for (x: Int in list) 11
}