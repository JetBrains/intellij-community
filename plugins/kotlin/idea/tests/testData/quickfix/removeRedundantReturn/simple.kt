
// LANGUAGE_VERSION: 2.3
// "Remove redundant return" "true"
fun foo(): String = <caret>return ""
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantReturnFix