// TEMPLATE_TEXT: top($EXPR$)
// CONDITION: kotlin.boolean
// USE_TOPMOST: true
fun test(flag: Boolean, other: Boolean, third: Boolean) {
    val x = when (flag) { true -> other; else -> top(third) }
}
