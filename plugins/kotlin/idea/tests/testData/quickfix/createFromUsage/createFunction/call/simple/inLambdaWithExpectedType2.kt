// "Create function 'createMe'" "true"
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: UNRESOLVED_REFERENCE
fun param(p: () -> Boolean) {
}

fun use(){
    param {
        <caret>createMe()
    }
}
