// "Make 'A' not open" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
<caret>open enum class A() {
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase