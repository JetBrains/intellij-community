// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:kotlin.Throwable
// USE_TOPMOST: false
fun test(t: Throwable) {
    t<caret>
}
