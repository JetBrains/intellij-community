// "Make type parameter reified and function inline" "true"
// K2_ERROR: Cannot check for instance of erased type 'T (of fun <T> test)'.
fun <T> test(a: String) = a is T<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeTypeParameterReifiedAndFunctionInlineFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeTypeParameterReifiedAndFunctionInlineFix