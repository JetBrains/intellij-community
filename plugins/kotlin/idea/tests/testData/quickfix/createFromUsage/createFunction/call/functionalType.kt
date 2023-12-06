// "Create function 'bar'" "true"

fun foo(block: (Int) -> String) {
    <caret>bar(block)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix