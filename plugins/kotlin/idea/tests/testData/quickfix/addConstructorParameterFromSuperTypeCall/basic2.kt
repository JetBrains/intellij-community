// "Add constructor parameter 'x'" "true"
// DISABLE_ERRORS
abstract class A(val x: Int, val y: String, val z: Long)
class B() : A(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix