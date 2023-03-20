// WITH_STDLIB
// AFTER-WARNING: The expression is unused
fun main() {
    <caret>for (x in 1.rangeTo(2)) {
        x
    }
}