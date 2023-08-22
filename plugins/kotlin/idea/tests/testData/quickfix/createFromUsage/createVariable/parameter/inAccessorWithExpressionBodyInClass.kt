// "Create property 'foo' as constructor parameter" "true"

class A {
    val test: Int get() = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix