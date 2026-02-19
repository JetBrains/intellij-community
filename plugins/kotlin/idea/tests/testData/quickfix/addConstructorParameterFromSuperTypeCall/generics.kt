// "Add constructor parameter 'foos'" "true"
abstract class Foo(foos: List<String>)
class Bar() : Foo(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix