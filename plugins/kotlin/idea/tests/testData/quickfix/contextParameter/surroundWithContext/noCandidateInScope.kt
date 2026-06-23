// "Surround call with 'context'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: No context argument for 'l: MyLogger' found.
// K2_AFTER_ERROR: No context argument for 'l: MyLogger' found.
interface MyLogger { fun log(msg: String) }

context(l: MyLogger) fun bar() {}

fun foo() {
    <caret>bar()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SurroundCallWithContextFix