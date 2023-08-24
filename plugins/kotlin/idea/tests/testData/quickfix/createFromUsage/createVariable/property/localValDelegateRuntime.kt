// "Create property 'foo'" "true"
// ERROR: Property must be initialized

fun test() {
    val x: Int by <caret>foo
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix