// "Create extension function 'List<T>.foo'" "true"
// WITH_STDLIB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class A<T>(val items: List<T>) {
    fun test(): Int {
        return items.<caret>foo<Int>(2, "2")
    }
}
