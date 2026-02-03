// "Add constructor parameter 'x'" "true"
abstract class Foo<T>(x: T)
class Boo : Foo<String>(<caret>)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddConstructorParameterFromSuperTypeCallFix