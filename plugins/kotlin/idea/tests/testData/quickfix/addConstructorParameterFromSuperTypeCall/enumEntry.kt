// "Add constructor parameter 'param'" "false"
// K2_AFTER_ERROR: No value passed for parameter 'param'.
enum class Foo(val param: String) {
    ENTRY(<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// IGNORE_K1