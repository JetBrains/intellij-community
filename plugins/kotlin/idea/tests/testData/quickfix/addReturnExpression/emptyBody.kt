// "Add 'return' expression" "true"
// WITH_STDLIB
// K2_ERROR: Missing return statement.
fun test(): Boolean {
}<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnExpressionFix