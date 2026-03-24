// "Remove 'final' modifier" "true"
// K2_ERROR: Modifier 'final' is not applicable to 'interface'.
<caret>final interface A {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase