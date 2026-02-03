// "Add 'noinline' to parameter 'block'" "true"

inline fun foo(block: () -> Unit) = block<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddInlineModifierFixFactories$AddInlineModifierFix