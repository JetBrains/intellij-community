// "Remove '?'" "true"
// K2_ERROR: Supertypes cannot be nullable.
open class Foo() {}
class Bar() : Foo?<caret>() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveNullableFix