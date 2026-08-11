// "Create extension function '((Int) -> String).bar'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo(block: (Int) -> String) {
    block.b<caret>ar()
}
