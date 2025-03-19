// "Create function 'createMe'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
fun param(p: (i: Int, s: String) -> Boolean) {
}

fun use(){
    param { i, s ->
        <caret>createMe(i, s)
    }
}
