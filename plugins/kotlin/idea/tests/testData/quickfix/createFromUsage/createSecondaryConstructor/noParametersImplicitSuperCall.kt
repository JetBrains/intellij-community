// "Create secondary constructor" "true"
open class Base()

class Creation {
    constructor(f: Int)
}
val v = Creation(<caret>)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix