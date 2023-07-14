// "Create function 'createMe'" "true"
fun param(p: () -> Boolean) {
}

fun use(){
    param {
        <caret>createMe()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix