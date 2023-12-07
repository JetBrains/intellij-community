// "Import property 'localStorage'" "true"
// JS_WITH_DOM_API_COMPAT

package test

fun foo() {
    <caret>localStorage
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.importFix.ImportQuickFix
