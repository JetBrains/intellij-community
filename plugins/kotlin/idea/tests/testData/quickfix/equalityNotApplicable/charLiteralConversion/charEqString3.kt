// "Convert string to character literal" "true"
// K2_ERROR: Operator '==' cannot be applied to 'Char' and 'String'.
fun test(c: Char): Boolean {
    return <caret>c == """a"""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix