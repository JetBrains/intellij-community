// "Create member function 'A.foo'" "true"
// ERROR: Type mismatch: inferred type is A<Int> but Int was expected
// K2_AFTER_ERROR: Return type mismatch: expected 'Int', actual 'A<Int>'.
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class A<T>(val n: T)

fun test(): Int {
    return A(1).<caret>foo("s", 1) as A<Int>
}
