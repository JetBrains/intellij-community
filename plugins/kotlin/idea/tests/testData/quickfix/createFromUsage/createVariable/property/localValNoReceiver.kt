// "Create property 'foo'" "false"
// K2_ACTION: "Create property 'foo'" "true"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: MUST_BE_INITIALIZED
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    fun nestedTest(): Int {
        return <caret>foo
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction