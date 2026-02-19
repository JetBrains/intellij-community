// "Inline type parameter" "true"

data class DC<T : Int<caret>>(val x: T, val y: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix