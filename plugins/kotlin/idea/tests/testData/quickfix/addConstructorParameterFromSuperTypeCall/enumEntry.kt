// "Add constructor parameter 'param'" "false"
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: NO_VALUE_FOR_PARAMETER
enum class Foo(val param: String) {
    ENTRY(<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
