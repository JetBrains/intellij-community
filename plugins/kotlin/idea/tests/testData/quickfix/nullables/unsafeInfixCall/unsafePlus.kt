// "Replace with safe (?.) call" "true"
// K2_ERROR: Operator call is prohibited on a nullable receiver of type 'Int?'. Use '?.'-qualified call instead.

operator fun Int.plus(index: Int) = this
fun fox(arg: Int?) = arg <caret>+ 42
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix