// "Create secondary constructor" "true"

class A {
    constructor(): this(<caret>1) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix