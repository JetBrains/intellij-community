// "Add 'inline' to function 'foo'" "true"
// K2_ERROR: ILLEGAL_INLINE_PARAMETER_MODIFIER

fun foo(<caret>crossinline body: () -> Unit) {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineToFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddInlineToFunctionFixFactories$AddInlineToFunctionFix