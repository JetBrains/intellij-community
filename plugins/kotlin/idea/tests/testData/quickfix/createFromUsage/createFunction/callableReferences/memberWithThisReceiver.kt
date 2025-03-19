// "Create member function 'Q.myf'" "true"
class R(val f: (Int) -> Unit)

class Q {
    val r = R(this::myf<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix