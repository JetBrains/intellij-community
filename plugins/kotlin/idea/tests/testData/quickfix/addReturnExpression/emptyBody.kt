// "Add 'return' expression" "true"
// WITH_STDLIB
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
fun test(): Boolean {
}<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnExpressionFix