// "Create property 'y' as constructor parameter" "true"
annotation class Annotation(val x: Int)

@Annotation(x = 1, <caret>y = 2)
class C
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: val y: kotlin.Int