// "Import class 'TestOnly'" "true"

@TestOnly<caret>
fun foo() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.importFix.ImportQuickFix