// "Create parameter 'newparam'" "true"

fun foo() {
    bar(
        new<caret>param = 22,
    )
}

fun bar(
    param: Int = 2,
) = Unit

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction