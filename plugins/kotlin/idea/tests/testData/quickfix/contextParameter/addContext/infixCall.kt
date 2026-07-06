// "Add context parameter to function" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters
// K2_ERROR: NO_CONTEXT_ARGUMENT
context(i: Int) infix fun String.with(n: Int): String = ""

fun foo() {
    val r = "hello" <caret>with 3
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddContextParameterFix$ForEnclosingFunction