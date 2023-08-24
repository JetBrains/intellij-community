// "Create property 'foo' as constructor parameter" "true"

class A<T>(val n: T) {
    fun test(): A<Int> {
        return this.<caret>foo
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix