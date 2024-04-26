// "Create member function 'SomeObj.doSomething'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
class SomeObj { }

fun doSomething(p: Any): List<Number>{
    if (p is SomeObj){
        p.<caret>doSomething()

    }
    return emptyList()
}
