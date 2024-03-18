// "Create member function 'SomeObj.doSomething'" "true"
class SomeObj { }

fun doSomething(p: Any): List<Number>{
    if (p is SomeObj){
        p.<caret>doSomething()

    }
    return emptyList()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix