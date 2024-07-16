// "Make type parameter reified and function inline" "true"
fun <T> test(a: String) = a is T<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeTypeParameterReifiedAndFunctionInlineFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MakeTypeParameterReifiedAndFunctionInlineFixFactory$MakeTypeParameterReifiedAndFunctionInlineFix