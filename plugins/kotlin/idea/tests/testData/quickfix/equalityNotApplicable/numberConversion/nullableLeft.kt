// "Convert left-hand side to 'Long'" "true"
// K2_ERROR: EQUALITY_NOT_APPLICABLE
fun test(s: Short?, l: Long?): Boolean {
    return <caret>s == l
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix