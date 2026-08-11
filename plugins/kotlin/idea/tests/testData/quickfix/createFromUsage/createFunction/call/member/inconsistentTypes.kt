// "Create member function 'A.foo'" "true"
// ERROR: Type mismatch: inferred type is A<Int> but Int was expected
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// K2_AFTER_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: RETURN_TYPE_MISMATCH
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T>(val n: T)

fun test(): Int {
    return A(1).<caret>foo("s", 1) as A<Int>
}
