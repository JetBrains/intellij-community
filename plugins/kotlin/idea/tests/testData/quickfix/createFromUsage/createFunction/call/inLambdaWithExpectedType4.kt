// "Create function 'createMe'" "true"
fun param(p: () -> Boolean, x: Int) {
}

fun use(){
    param({ <caret>createMe() }, 1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix