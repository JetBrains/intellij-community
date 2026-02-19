// "Add 'crossinline' to parameter 'x'" "true"

inline fun foo(<caret>x: suspend () -> Unit) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddInlineModifierFixFactories$AddInlineModifierFix