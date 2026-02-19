// "Create member function 'B.foo'" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class B<T>(val t: T) {

}

class A<T>(val b: B<T>) {
    fun test(): Int {
        return b.<caret>foo<String>(2, "2")
    }
}
