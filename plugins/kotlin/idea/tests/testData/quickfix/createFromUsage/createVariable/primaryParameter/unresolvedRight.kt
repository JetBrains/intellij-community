// "Create property 'foo' as constructor parameter" "true"
// ERROR: Unresolved reference: unresolved
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

class A {
    fun expressionBody() {
        f<caret>oo = unresolved
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction