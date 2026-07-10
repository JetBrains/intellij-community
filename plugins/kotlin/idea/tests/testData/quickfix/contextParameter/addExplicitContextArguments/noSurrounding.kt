// "Add explicit context argument" "true"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(x: String)
fun foo3(): String = x

fun main() {
    <caret>foo3("Hello")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix