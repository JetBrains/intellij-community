// "Convert string to character literal" "true"
// K2_ERROR: Operator '==' cannot be applied to 'String' and 'Char'.
fun test(c: Char): Boolean {
    return <caret>"\t" == c
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix