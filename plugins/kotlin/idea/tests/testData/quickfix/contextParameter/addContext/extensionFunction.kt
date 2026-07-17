// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(i: Int) fun bar() {}

fun String.foo() {
    <caret>bar()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForEnclosingFunction