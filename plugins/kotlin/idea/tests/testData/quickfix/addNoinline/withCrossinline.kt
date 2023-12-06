// "Add 'noinline' to parameter 'lambda'" "true"
// WITH_STDLIB
inline fun inlineFun(crossinline lambda: () -> Unit) {
    <caret>lambda.toString()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineModifierFix