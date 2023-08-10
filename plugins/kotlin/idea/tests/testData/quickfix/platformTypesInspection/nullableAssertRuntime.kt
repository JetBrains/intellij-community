// "Add non-null asserted (!!) call" "true"

fun bar<caret>() = java.lang.String.valueOf(1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix