// "Create abstract property 'foo'" "true"
interface AbstractFromAssignment {
    fun defaultFun() {
        fo<caret>o = "gg"
    }
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreatePropertyFromUsageBuilder$CreatePropertyFromUsageAction