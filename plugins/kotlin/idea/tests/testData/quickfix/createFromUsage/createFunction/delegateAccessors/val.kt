// "Create member function 'F.getValue'" "true"
class F {

}

class X {
    val f: Int by F()<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix