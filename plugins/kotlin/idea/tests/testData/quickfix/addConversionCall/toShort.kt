// "Convert expression to 'Short'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'Short' was expected.

fun takeShort(x: Short) {}

fun foo() {
    takeShort(1 + 1<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConversionCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.NumberConversionFix