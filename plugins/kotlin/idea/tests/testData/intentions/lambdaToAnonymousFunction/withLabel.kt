// WITH_RUNTIME

fun testAAA(action: () -> Unit) = Unit
fun t() {
    testAAA label@<caret>{ println(42) }
}
