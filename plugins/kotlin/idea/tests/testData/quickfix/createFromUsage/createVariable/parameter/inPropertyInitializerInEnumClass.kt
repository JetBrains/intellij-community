// "Create parameter 'foo'" "true"
// ERROR: No value passed for parameter 'foo'
// ERROR: No value passed for parameter 'foo'
// ERROR: No value passed for parameter 'foo'
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: UNRESOLVED_REFERENCE

enum class E {
    A,
    B,
    C;

    val t: Int = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: foo: kotlin.Int