// "Add constructor parameter 'y'" "true"
// DISABLE_ERRORS
abstract class A(val x: Int, val y: String, val z: Long)
class B(x: Int) : A(x<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix