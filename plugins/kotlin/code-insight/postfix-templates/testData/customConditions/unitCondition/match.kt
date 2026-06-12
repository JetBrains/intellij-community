// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.unit
// USE_TOPMOST: false
fun produce() {}

fun test() {
    produce()<caret>
}
