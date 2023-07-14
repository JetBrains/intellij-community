// "Create member function 'A.foo'" "true"

class A

fun test() {
    println("a = ${A().<caret>foo()}")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix