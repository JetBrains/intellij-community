// "Import property 'asserter'" "true"
// RUNTIME_WITH_KOTLIN_TEST
// K2_ERROR: Unresolved reference 'asserter'.

package test

fun foo() {
    <caret>asserter
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix