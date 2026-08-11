// "Remove 'enum' modifier" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
class A() {
    <caret>enum fun foo() {}
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase