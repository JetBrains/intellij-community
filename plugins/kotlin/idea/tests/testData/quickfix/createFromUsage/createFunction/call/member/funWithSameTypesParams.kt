// "Create function 'foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: Unresolved reference 'foo'.
class A {
    fun m() {
        fo<caret>o("a", "b", "c")
    }
}