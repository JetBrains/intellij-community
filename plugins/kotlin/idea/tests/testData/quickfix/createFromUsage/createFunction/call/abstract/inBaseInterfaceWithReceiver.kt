// "Create abstract function 'I.bar'" "true"
// K2_ERROR: Unresolved reference 'bar'.

interface I

fun test(i: I) {
    i.<caret>bar()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction