// INTENTION_TEXT: Convert to vararg parameter (may break code)
fun test(<caret>a: Array<Int?>) {
    a[0]?.unaryPlus()
}
