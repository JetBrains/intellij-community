// "Create parameter 'foo'" "true"
// K2_ERROR: Unresolved reference 'foo' on receiver of type 'String'.

fun bar() {
    val p: String = "abc".fo<caret>o()
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// TEST_PREVIEW: foo: kotlin.String.() -> kotlin.String