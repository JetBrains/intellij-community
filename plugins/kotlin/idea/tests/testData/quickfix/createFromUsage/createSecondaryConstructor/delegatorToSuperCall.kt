// "Create secondary constructor" "true"

open class A {

}

class B: A(<caret>1) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix