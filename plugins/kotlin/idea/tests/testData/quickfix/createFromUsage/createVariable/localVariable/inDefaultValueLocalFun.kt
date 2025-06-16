// "Create local variable 'abc'" "true"
// WITH_STDLIB
class Test {
    fun outer() {
        fun testMethod(x:Int = <caret>abc) {

        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateLocalVariableFromUsageBuilder$CreateLocalFromUsageAction