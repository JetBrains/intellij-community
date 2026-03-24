// "Change type arguments to <*>" "true"
// K2_ERROR: Cannot check for instance of erased type 'List<String>?'.
fun isStringList(list : Any) = list is List<<caret>String>?
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix