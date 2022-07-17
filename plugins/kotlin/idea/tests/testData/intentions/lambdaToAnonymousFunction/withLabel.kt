// WITH_STDLIB
// AFTER-WARNING: Parameter 'action' is never used

fun testAAA(action: () -> Unit) = Unit
fun t() {
    testAAA label@<caret>{ println(42) }
}
