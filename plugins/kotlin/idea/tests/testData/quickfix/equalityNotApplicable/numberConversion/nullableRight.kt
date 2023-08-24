// "Convert right-hand side to 'Short'" "true"
fun test(s: Short?, l: Long?): Boolean {
    return <caret>s == l
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix