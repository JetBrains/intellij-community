// "Add 'noinline' to parameter 'block'" "true"
// K2_ERROR: USAGE_IS_NOT_INLINABLE

inline fun foo(block: () -> Unit) = block<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddInlineModifierFixFactories$AddInlineModifierFix