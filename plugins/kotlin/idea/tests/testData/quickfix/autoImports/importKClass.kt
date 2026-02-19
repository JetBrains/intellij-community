// "Import class 'KClass'" "true"
// WITH_STDLIB

fun foo(x: <caret>KClass<Int>) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix