// "Replace with safe (?.) call" "true"

operator fun Int.get(row: Int, column: Int) = this
fun foo(arg: Int?) = arg<caret>[42, 13]
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix