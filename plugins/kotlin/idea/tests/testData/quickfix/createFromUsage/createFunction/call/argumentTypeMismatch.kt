// "Create function 'foo'" "true"
fun foo(n: Int) {}

fun test() {
    foo("a<caret>bc${1}")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix