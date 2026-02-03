// "Create member function 'A.plus'" "true"

class A<T>(val n: T)

fun test() {
    var a = A(1)
    a = a <caret>+ 2
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix