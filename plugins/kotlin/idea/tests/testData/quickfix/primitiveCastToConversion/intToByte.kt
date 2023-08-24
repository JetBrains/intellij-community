// "Replace cast with call to 'toByte()'" "true"

fun foo() {
    val a = 1 as<caret> Byte
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplacePrimitiveCastWithNumberConversionFix