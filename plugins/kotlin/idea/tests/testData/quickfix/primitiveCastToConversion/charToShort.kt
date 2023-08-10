// "Replace cast with call to 'toShort()'" "true"

fun foo(c: Char) {
    val a = c as<caret> Short
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplacePrimitiveCastWithNumberConversionFix