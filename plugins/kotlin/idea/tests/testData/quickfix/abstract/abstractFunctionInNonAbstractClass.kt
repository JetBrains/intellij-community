// "Make 'foo' not abstract" "true"
// K2_ERROR: ABSTRACT_FUNCTION_IN_NON_ABSTRACT_CLASS
// K2_ERROR: ABSTRACT_FUNCTION_WITH_BODY
class A() {
    <caret>abstract fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase