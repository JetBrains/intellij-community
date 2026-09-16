// "Remove 'final' modifier" "true"
// K2_ERROR: WRONG_MODIFIER_TARGET
<caret>final interface A {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase