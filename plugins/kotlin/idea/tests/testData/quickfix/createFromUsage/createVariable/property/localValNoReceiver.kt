// "Create property 'foo'" "false"
// K2_ACTION: "Create property 'foo'" "true"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Property must be initialized.

fun test() {
    fun nestedTest(): Int {
        return <caret>foo
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction