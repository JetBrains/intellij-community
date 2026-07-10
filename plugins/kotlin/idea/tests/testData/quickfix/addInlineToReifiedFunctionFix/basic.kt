// "Add 'inline' modifier" "true"
// K2_ERROR: REIFIED_TYPE_PARAMETER_NO_INLINE

fun <<caret>reified T> fn() {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp