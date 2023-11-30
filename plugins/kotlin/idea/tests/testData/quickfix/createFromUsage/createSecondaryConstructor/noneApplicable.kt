// "Create secondary constructor" "true"
// ERROR: Primary constructor call expected

class CtorSecondary() {
    constructor(p: Int) : this()
}

fun construct() {
    // todo: add this()
    val vA = <caret>CtorSecondary(2, 3)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix