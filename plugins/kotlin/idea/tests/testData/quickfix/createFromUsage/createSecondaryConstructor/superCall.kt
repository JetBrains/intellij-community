// "Create secondary constructor" "true"

open class A {

}

class B: A {
    constructor(): super(<caret>1) {

    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix