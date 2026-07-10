// "Create property 'foo' as constructor parameter" "true"
// ERROR: No value passed for parameter 'foo'
// K2_AFTER_ERROR: NO_VALUE_FOR_PARAMETER
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T>(val n: T)

fun test() {
    A(1).<caret>foo = "1"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction