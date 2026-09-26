// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.nonUnit
// USE_TOPMOST: false
fun produce() {}

fun test() {
    produce()<caret>
}
