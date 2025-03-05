// "Create secondary constructor" "true"
// DISABLE_ERRORS

class CtorPrimary(val f1: Int, val f2: String)

fun construct() {
    val v6 = CtorPrimary(1, 2, 3<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix