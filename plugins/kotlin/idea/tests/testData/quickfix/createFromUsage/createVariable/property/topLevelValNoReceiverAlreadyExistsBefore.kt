// "Create property 'foo'" "true"
// ERROR: Property must be initialized

val bar = 1

fun test(): Int {
    return <caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix