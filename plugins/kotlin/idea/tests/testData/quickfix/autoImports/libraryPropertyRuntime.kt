// "Import property 'asserter'" "true"
// RUNTIME_WITH_KOTLIN_TEST

package test

fun foo() {
    <caret>asserter
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ImportQuickFix