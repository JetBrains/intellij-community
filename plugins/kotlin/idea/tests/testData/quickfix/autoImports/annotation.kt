// "Import class 'TestOnly'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

@TestOnly<caret>
fun foo() {}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix