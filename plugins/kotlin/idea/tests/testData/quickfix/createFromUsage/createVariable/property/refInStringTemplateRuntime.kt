// "Create property 'foo'" "true"
// ERROR: Property must be initialized

fun test() {
    println("a = $<caret>foo")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix