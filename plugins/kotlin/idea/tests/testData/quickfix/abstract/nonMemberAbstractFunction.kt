// "Make 'foo' not abstract" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
<caret>abstract fun foo() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase