// "Add 'inline' modifier" "true"

fun <<caret>reified T> fn() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineToFunctionWithReifiedFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix