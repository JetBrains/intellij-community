// "Surround call with 'with(i)'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// API_VERSION: 2.1
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(i: Int) fun bar() {}

fun foo(i: Int) {
    <caret>bar()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix