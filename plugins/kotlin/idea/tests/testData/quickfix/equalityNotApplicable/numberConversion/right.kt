// "Convert right-hand side to 'Byte'" "true"
fun test(b: Byte, i: Int): Boolean {
    return <caret>b == i
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix