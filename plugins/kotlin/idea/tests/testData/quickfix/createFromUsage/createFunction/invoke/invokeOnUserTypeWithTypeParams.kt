// "Create member function 'A.invoke'" "true"

class A<T>(val n: T)
class B<T>(val m: T)

fun <U, V> test(u: U): B<V> {
    return A(u)<caret>(u, "u")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix