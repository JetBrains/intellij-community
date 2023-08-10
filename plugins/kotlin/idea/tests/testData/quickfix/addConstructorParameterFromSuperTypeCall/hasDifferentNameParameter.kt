// "Add constructor parameter 'y'" "true"
// DISABLE-ERRORS
abstract class A(val x: Int, val y: String, val z: Long)
class B(a: Int, b: String, c: Long) : A(a<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix