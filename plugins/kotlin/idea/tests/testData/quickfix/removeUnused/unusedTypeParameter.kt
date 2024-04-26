// "Safe delete 'T'" "true"
class UnusedTypeParameter<<caret>T, P>(val p: P)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.highlighting.SafeDeleteFix