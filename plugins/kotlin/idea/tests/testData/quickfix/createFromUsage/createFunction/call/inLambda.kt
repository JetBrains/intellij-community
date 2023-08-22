// "Create function 'foo'" "true"

fun <T> run(f: () -> T) = f()

fun test() {
    run { <caret>foo() }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix