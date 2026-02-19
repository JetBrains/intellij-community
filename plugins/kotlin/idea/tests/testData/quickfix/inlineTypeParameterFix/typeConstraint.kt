// "Inline type parameter" "true"
fun <T> test(p: T, q: T) where T : String<caret> = p.length + q.length
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InlineTypeParameterFix