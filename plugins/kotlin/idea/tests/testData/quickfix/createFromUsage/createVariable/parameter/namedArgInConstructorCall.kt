// "Create parameter 's'" "true"

class Foo(val n: Int) {

}

fun bar() {
    Foo(n = 1, <caret>s = "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: s: kotlin.String