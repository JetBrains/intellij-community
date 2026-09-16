// "Import class 'KType'" "true"
// WITH_STDLIB
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo(x: <caret>KType) {}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix