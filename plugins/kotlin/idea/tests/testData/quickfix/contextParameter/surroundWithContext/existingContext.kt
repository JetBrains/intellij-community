// "Surround call with 'context'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_AFTER_ERROR: NO_CONTEXT_ARGUMENT
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(i: Int, s: String) fun bar() {}

fun foo() {
    context(1) {
        <caret>bar()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix