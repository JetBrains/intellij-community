// "Create function 'createMe'" "true"
fun param(p: (i: Int, s: String) -> Boolean) {
}

fun use(){
    param { i, s ->
        <caret>createMe(i, s)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix