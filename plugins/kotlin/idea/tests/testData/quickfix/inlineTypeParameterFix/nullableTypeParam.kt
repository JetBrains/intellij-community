// "Inline type parameter" "true"

fun <T: Int<caret>> foo(x: T?) {
    val y: T = x!!
}

fun main() {
    foo(x = null)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix