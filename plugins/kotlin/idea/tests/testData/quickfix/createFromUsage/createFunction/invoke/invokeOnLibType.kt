// "Create extension function 'Int.invoke'" "true"
// WITH_STDLIB

class A<T>(val n: T)

fun test(): A<String> {
    return <caret>1(2, "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix