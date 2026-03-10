// "Replace with safe (?.) call" "true"
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Int?'.

operator fun Int.set(row: Int, column: Int, value: Int) {}
fun foo(arg: Int?) {
    arg<caret>[42, 13] = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix