// "Make 'foo' not abstract" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
<caret>abstract fun foo() {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase