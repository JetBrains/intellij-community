// "Remove 'override' modifier" "true"
// K2_ERROR: 'foo' overrides nothing.
class A() {
    <caret>override fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase