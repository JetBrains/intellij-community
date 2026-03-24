// "Make 'i' not abstract" "true"
// K2_ERROR: Modifier 'abstract' is not applicable to 'top level property without backing field or delegate'.
<caret>abstract val i: Int = 1

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase