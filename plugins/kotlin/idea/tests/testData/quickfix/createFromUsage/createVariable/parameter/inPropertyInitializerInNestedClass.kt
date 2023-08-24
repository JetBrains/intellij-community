// "Create parameter 'foo'" "true"

class A {
    class B {
        val test: Int = <caret>foo
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix