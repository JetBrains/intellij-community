// "Create parameter 'foo'" "true"

class A<T> {
    fun <T> test(n: Int) {
        val t: T = <caret>foo
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix