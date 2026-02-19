// "Convert string to character literal" "true"
fun test(c: Char): Boolean {
    return <caret>c == "\""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertStringToCharLiteralFixFactory$ConvertStringToCharLiteralFix