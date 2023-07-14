// "Change type arguments to <*, *>" "true"
fun isStringToIntMap(map : Any) = map is Map<<caret>String, Int>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix