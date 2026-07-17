// "Replace with safe (?.) call" "true"
// K2_ERROR: INFIX_MODIFIER_REQUIRED
// K2_ERROR: UNSAFE_INFIX_CALL
fun test(a : Int?) : Int? {
    return a <caret>compareTo 6;
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix