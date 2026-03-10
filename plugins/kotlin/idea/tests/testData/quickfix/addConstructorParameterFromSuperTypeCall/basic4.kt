// "Add constructor parameter 'z'" "true"
// K2_ERROR: No value passed for parameter 'z'.
abstract class A(val x: Int, val y: String, val z: Long)
class B(x: Int, y: String) : A(x, y<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix