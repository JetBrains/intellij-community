// "Convert left-hand side to 'Int'" "true"
// K2_ERROR: Operator '==' cannot be applied to 'Byte' and 'Int'.
fun test(b: Byte, i: Int): Boolean {
    return <caret>b == i
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix