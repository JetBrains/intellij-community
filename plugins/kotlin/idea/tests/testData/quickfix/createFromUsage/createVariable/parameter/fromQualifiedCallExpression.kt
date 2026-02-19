// "Create parameter 'foo'" "true"

fun bar() {
    val p: String = "abc".fo<caret>o()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: foo: kotlin.String.() -> kotlin.String