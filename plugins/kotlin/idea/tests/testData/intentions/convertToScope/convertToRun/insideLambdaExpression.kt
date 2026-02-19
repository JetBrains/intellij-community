// AFTER-WARNING: Variable 'c' is never used
// AFTER-WARNING: Parameter 't' is never used
// AFTER-WARNING: The expression is unused
// WITH_STDLIB
fun t() {
    val a = 42
    return ac {
        a.dec()
        val c = <caret>a.dec()
        42
    }
}
fun ac(t: () -> Unit) = Unit