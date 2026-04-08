// IGNORE_K2
// Ignore reason: 'fun toShort(): Short' is deprecated since kotlin 1.5. Conversion of Char to Number is deprecated. Use Char.code property instead.
// "Replace cast with call to 'toShort()'" "true"

fun foo(c: Char) {
    val a = c as<caret> Short
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplacePrimitiveCastWithNumberConversionFix
