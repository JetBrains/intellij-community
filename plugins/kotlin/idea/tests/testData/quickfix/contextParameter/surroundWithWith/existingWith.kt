// "Surround call with 'with'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// API_VERSION: 2.1

// DISABLE_K2_ERRORS
// QuickFix produces red code with caret position to fill missing argument
context(i: Int, s: String) fun bar() {}

fun foo() {
    with(1) {
        <caret>bar()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix