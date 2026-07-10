// "Create parameter 'foo'" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T> {
    fun test(n: Int) {
        val t: T = <caret>foo
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: foo: T