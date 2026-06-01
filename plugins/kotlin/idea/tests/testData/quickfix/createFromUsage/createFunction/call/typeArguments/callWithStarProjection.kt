// "Create function 'foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_ERROR: Projections are not allowed on type arguments of functions calls.
// K2_ERROR: Unresolved reference 'foo'.
// K2_AFTER_ERROR: One type argument expected for 'fun <T> foo(i: Int, t: T): Int'.
// K2_AFTER_ERROR: Projections are not allowed on type arguments of functions calls.

fun test(): Int {
    return <caret>foo<String, *>(2, "2")
}
