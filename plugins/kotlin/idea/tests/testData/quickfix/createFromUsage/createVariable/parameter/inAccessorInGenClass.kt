// "Create property 'foo' as constructor parameter" "true"

class A<T> {
    val test: T get() {
        return <caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix