// "Create member function 'A.foo'" "true"
// ERROR: Type mismatch: inferred type is A<Int> but Int was expected

class A<T>(val n: T)

fun test(): Int {
    return A(1).<caret>foo("s", 1) as A<Int>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix