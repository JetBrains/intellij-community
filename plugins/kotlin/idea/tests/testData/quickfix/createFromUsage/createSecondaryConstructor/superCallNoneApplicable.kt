// "Create secondary constructor" "true"

open class A {
    constructor(x: Int, y: Int)
    constructor(x: Int, y: String)
}

class B : <caret>A(1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix