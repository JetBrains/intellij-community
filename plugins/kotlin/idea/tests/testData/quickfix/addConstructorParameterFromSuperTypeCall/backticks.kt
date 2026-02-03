// "Add constructor parameter '`first param`'" "true"
// DISABLE_ERRORS
class `My class`
open class A(`first param`: `My class`)
class B : A(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix