// "Add 'noinline' to parameter 'x'" "true"
// TODO: remove it after coroutines are released
// DISABLE-ERRORS

inline fun foo(<caret>x: suspend () -> Unit) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix