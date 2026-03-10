// "Remove 'enum' modifier" "true"
// K2_ERROR: Modifier 'enum' is not applicable to 'interface'.
<caret>enum interface A {
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase