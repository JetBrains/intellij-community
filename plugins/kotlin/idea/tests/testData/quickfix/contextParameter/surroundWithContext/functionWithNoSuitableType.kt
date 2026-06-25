// "Surround call with 'context(TODO())'" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: No context argument for 's: String' found.
context(s: String) fun bar() {}

fun foo(i: Int) {
    <caret>bar()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix