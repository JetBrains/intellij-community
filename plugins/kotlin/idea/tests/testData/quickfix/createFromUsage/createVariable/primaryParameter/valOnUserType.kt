// "Create property 'foo' as constructor parameter" "true"
// ERROR: No value passed for parameter 'foo'

class A<T>(val n: T)

fun test() {
    val a: A<Int> = A(1).<caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix