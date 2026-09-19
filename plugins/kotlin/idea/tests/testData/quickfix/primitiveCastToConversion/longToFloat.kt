// "Replace cast with call to 'toFloat()'" "true"

fun foo() {
    val a = 1L as<caret> Float
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplacePrimitiveCastWithNumberConversionFix