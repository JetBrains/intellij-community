// "Create parameter 'foo'" "true"

class A {
    fun test(n: Int) {
        val t: Int = <caret>foo
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix