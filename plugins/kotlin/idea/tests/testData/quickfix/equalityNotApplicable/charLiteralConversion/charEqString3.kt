// "Convert string to character literal" "true"
fun test(c: Char): Boolean {
    return <caret>c == """a"""
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertStringToCharLiteralFix