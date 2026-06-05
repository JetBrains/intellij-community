// "Apply all 'Change to star projection' fixes in file" "true"
// K2_ERROR: Cannot check for instance of erased type 'Map<Long, String>'.
// K2_ERROR: Cannot check for instance of erased type 'Map<String, Int>'.

fun isStringToIntMap(map: Any) = map is Map<<caret>String, Int>
fun isLongToStringMap(map: Any) = map is Map<Long, String>

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
