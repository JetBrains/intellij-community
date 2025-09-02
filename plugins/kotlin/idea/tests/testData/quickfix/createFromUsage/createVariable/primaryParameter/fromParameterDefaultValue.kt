// "Create property 'foo' as constructor parameter" "true"
// ERROR: Parameter 'foo' is uninitialized here

class CtorAccess(val prop: Int = fo<caret>o)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction