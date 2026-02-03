// "Create member function 'Outer.Inner.foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class Outer {
    inner class Inner()
    val innies = ArrayList<Inner>()
    fun run() {
        innies.forEach { it.fo<caret>o() }
    }
}
