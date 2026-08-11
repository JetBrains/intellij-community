// "Apply all 'Change to star projection' fixes in file" "true"
// K2_ERROR: CANNOT_CHECK_FOR_ERASED
// K2_ERROR: CANNOT_CHECK_FOR_ERASED

fun isStringToIntMap(map: Any) = map is Map<<caret>String, Int>
fun isLongToStringMap(map: Any) = map is Map<Long, String>

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
