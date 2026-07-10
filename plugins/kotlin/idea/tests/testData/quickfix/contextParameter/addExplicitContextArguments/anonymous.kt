// "Add explicit context argument" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(_: String)
fun ctxFun(value: Int) {}

fun useAC3() {
    <caret>ctxFun(0)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix