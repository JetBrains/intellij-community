// "Create property 'foo'" "true"
// ERROR: Property must be initialized or be abstract

class Test {
    fun test(): Int {
        return <caret>foo
    }

    val foo1 = 1
}

val bar = 1

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix