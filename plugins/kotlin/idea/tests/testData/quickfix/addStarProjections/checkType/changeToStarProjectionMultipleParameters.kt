// "Change type arguments to <*, *>" "true"
// K2_ERROR: CANNOT_CHECK_FOR_ERASED
fun isStringToIntMap(map : Any) = map is Map<<caret>String, Int>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix