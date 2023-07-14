// "Add constructor parameter 'y'" "true"
// DISABLE-ERRORS
abstract class A(val x: Int, val y: String, val z: Long)
class B(x: Int) : A(x<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix