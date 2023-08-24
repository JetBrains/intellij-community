// "Create member function 'A.invoke'" "true"

class A<T>(val n: T)
class B<T>(val m: T)

fun test(): B<String> {
    return A(1)<caret>(2, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix