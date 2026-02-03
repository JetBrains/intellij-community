// "Create secondary constructor" "true"

class A {

}

fun test() {
    val a = A(<caret>1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix