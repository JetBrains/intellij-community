// "Add 'noinline' to parameter 'lambda'" "true"
// WITH_STDLIB
// K2_ERROR: Illegal usage of inline parameter 'lambda: () -> Unit'. Add 'noinline' modifier to the parameter declaration.

inline fun inlineFun(lambda: () -> Unit) {
    <caret>lambda.let { }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddInlineModifierFixFactories$AddInlineModifierFix