// "Create property 'foo' as constructor parameter" "true"

class A<T>(val n: T) {
    inner class B<U>(val m: U) {
        fun test(): A<Int> {
            return this@A.<caret>foo
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction