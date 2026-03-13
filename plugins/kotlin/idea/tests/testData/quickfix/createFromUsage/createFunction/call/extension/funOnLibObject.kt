// "Create extension function 'Unit.foo'" "true"
// WITH_STDLIB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: Unresolved reference 'foo'.

fun test() {
    val a: Int = Unit.<caret>foo(2)
}
