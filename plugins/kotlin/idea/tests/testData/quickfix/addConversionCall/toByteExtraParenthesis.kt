// "Convert expression to 'Byte'" "true"

fun takeByte(x: Byte) {}

fun foo() {
    takeByte(1 + (1 + 1)<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConversionCallFix