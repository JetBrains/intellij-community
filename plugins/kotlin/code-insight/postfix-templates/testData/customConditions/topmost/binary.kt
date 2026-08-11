// TEMPLATE_TEXT: top($EXPR$)
// CONDITION: kotlin.boolean
// USE_TOPMOST: true
fun test(flag: Boolean, other: Boolean) {
    val x = flag || other<caret>
}
