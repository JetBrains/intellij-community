// "Create property 'foo' as constructor parameter" "true"
// K2_ERROR: Unresolved reference 'foo' on receiver of type 'A<T (of class A<T>)>.B<U (of class B<U, Outer(T)>)>'.

class A<T>(val n: T) {
    inner class B<U>(val m: U) {
        fun test(): A<Int> {
            return this.<caret>foo
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction