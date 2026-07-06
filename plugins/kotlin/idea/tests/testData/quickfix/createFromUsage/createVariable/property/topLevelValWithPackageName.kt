// "Create property 'foo'" "true"
// ERROR: Property must be initialized
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_ERROR: EXPRESSION_EXPECTED_PACKAGE_FOUND
// K2_ERROR: RETURN_TYPE_MISMATCH

package foo

fun test(): Int {
    return <caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction