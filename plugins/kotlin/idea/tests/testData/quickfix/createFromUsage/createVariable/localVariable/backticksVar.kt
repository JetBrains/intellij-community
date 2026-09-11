// "Create local variable '`my-var`'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    println(<caret>`my-var`) // ← undefined variable with backticks
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateLocalVariableFromUsageBuilder$CreateLocalFromUsageAction