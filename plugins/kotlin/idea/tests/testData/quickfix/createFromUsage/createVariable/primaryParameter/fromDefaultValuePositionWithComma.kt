// "Create property 'foobs' as constructor parameter" "true"

data class Foo(val bar: Int)

fun baz(foo: Foo): Foo {
    Foo(bar = 1, fo<caret>obs = "")
    return foo.copy(
        bar = foo.bar + 1,
    )
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateParameterFromUsageBuilder$CreateParameterFromUsageAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix