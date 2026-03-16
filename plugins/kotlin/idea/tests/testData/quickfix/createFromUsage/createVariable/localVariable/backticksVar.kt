// "Create local variable '`my-var`'" "true"

fun test() {
    println(<caret>`my-var`) // ← undefined variable with backticks
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateLocalVariableFromUsageBuilder$CreateLocalFromUsageAction