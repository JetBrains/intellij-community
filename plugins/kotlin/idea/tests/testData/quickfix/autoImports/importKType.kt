// "Import class 'KType'" "true"
// WITH_STDLIB

fun foo(x: <caret>KType) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ImportQuickFix