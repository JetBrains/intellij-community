// "Remove 'final' modifier" "true"

@Deprecated("")
/* some comment */
final<caret> val x: Int = 42

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase