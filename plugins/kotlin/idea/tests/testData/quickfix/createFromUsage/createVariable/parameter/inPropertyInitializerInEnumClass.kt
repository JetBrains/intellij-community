// "Create parameter 'foo'" "true"
// ERROR: No value passed for parameter 'foo'
// ERROR: No value passed for parameter 'foo'
// ERROR: No value passed for parameter 'foo'
// K2_AFTER_ERROR: No value passed for parameter 'foo'.
// K2_AFTER_ERROR: No value passed for parameter 'foo'.
// K2_AFTER_ERROR: No value passed for parameter 'foo'.

enum class E {
    A,
    B,
    C;

    val t: Int = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction