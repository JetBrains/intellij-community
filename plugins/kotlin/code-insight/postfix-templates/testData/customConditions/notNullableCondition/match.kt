// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.notNullable
// USE_TOPMOST: false
fun test(s: String) {
    s<caret>
}
