// "Add explicit context arguments" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(_: String, i: Int)
fun ctxFun(value: Int) {}

fun useMixed() {
    <caret>ctxFun(0)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix