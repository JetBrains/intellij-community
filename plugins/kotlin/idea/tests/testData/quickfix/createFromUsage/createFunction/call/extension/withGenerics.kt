// "Create extension function 'Wrapper<String>.foo'" "true"
// WITH_STDLIB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction

class Wrapper<T>

fun foo(a: Wrapper<String>) {
    a.f<caret>oo()
}
