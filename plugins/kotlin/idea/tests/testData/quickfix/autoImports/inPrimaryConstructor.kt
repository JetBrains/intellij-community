// "Import class 'LinkedList'" "true"
// RUNTIME_WITH_FULL_JDK
// K2_ERROR: UNRESOLVED_REFERENCE

class LL(val list: <caret>LinkedList<String>)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix