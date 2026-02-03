// "Remove final upper bound" "true"

data class DC<T>(val x: T, val y: String) where T : Int<caret>, T : Comparable<Int>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFinalUpperBoundFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveFinalUpperBoundFix