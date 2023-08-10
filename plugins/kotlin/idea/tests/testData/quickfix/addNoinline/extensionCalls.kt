// "Add 'noinline' to parameter 'lambda'" "true"
// WITH_STDLIB

inline fun inlineFun(lambda: () -> Unit) {
    <caret>lambda.let { }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix