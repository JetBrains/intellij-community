// "Create member function 'A.unaryMinus'" "true"

class A<T>(val n: T)

fun test() {
    val a = <caret>-A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix