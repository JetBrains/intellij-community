// "Add 'crossinline' to parameter 'x'" "true"

inline fun foo(<caret>x: suspend () -> Unit) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix