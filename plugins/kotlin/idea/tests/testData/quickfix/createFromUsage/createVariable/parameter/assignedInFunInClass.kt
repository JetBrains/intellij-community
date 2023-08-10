// "Create property 'foo' as constructor parameter" "true"

class A {
    fun test(n: Int) {
        <caret>foo = n + 1
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix