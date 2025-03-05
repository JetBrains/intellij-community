// "Create local variable 'foo'" "true"
//TODO K2 incorrectly deduces the type, see KT-67250
// K2_AFTER_ERROR: Return type mismatch: expected 'Int', actual 'Nothing?'.
class A {
    val t: Int get() = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateLocalVariableFromUsageBuilder$CreateLocalFromUsageAction