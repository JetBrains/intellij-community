// "Remove 'inner' modifier" "true"
// K2_ERROR: WRONG_MODIFIER_CONTAINING_DECLARATION
interface A {
    inne<caret>r class B
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase