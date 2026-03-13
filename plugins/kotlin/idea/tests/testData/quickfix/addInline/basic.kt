// "Add 'inline' to function 'foo'" "true"
// K2_ERROR: Modifier is only allowed for function parameters of an inline function.

fun foo(<caret>crossinline body: () -> Unit) {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineToFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddInlineToFunctionFixFactories$AddInlineToFunctionFix