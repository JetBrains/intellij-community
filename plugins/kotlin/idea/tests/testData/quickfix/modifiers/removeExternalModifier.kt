// "Remove 'external' modifier" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET

class B {
    <caret>external val foo: Int = 23
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase