// WITH_RUNTIME
// AFTER-WARNING: The expression is unused
fun foo() {
    val x = 1..4

    x.<caret>forEach { it }
}