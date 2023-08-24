// "Create property 'foo' as constructor parameter" "true"

class A {
    class B {
        fun test(): Int {
            return <caret>foo
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix