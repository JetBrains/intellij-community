// "Create member function 'A.contains'" "true"

class A<T>(val n: T)

fun test() {
    2 <caret>in A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix