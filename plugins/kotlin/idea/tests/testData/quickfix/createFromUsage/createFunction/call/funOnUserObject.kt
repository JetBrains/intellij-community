// "Create member function 'A.foo'" "true"

object A

fun test() {
    val a: Int = A.<caret>foo(2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix