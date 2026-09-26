// TEMPLATE_TEXT: top($EXPR$)
// CONDITION: kotlin.boolean
// USE_TOPMOST: true
fun test(flag: Boolean, other: Boolean, third: Boolean) {
    val x = top(if (flag) other else third)
}
