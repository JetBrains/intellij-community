// "Create secondary constructor" "true"

interface T

class A: T

fun test() {
    val t: T = A(<caret>1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix