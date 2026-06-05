// "Apply all 'Change to star projection' fixes in file" "true"
// K2_ERROR: Cannot check for instance of erased type 'List<Int>'.
// K2_ERROR: Cannot check for instance of erased type 'List<String>'.

fun isStringList(list: Any?) = list is (List<<caret>String>)
fun isIntList(list: Any?) = list is (List<Int>)

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
