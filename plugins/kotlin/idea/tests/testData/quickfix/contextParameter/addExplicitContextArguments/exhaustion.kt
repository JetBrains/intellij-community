// "Add explicit context argument" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(s: String)
fun bar(required: String): String = ""

fun foo() {
    <caret>bar("only-positional")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix