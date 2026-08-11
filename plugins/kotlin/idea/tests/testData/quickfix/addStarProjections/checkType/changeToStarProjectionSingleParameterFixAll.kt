// "Apply all 'Change to star projection' fixes in file" "true"
// K2_ERROR: CANNOT_CHECK_FOR_ERASED
// K2_ERROR: CANNOT_CHECK_FOR_ERASED

fun isStringList(list: Any?) = list is (List<<caret>String>)
fun isIntList(list: Any?) = list is (List<Int>)

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
