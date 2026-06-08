// "Surround call with 'context'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters

// DISABLE_K2_ERRORS
// QuickFix produces red code with caret position to fill missing argument
context(i: Int, s: String) fun bar() {}

fun foo() {
    context(1) {
        <caret>bar()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix