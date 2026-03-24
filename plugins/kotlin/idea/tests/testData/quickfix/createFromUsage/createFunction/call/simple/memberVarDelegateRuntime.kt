// "Create function 'foo'" "true"
// K2_ERROR: Unresolved reference 'foo'.


class A<T>(val t: T) {
    var x: A<Int> by <caret>foo(t, "")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction