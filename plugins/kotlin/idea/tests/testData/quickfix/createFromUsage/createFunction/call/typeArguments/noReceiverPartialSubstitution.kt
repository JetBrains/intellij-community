// "Create function 'foo'" "true"

fun test(): Int {
    return <caret>foo<Int>(2, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix