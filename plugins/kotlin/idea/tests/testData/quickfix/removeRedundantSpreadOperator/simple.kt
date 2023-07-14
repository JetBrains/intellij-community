// "Remove redundant *" "true"
// LANGUAGE_VERSION: 1.4

fun takeVararg(vararg s: String) {}

fun test(strings: Array<String>) {
    takeVararg(s = *<caret>strings)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantSpreadOperatorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantSpreadOperatorFix