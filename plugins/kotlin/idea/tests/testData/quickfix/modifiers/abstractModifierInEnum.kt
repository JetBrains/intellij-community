// "Make 'A' not abstract" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
<caret>abstract enum class A() {
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase