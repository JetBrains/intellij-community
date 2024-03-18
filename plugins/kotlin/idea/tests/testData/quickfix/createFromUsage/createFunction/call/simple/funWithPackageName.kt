// "Create function 'foo'" "true"

package foo

fun test() {
    <caret>foo(2, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix