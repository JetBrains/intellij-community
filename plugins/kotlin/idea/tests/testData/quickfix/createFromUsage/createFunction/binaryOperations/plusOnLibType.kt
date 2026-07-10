// "Create extension function 'Int.plus'" "true"
// WITH_STDLIB
// K2_ERROR: NONE_APPLICABLE

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2 <caret>+ A(1)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction