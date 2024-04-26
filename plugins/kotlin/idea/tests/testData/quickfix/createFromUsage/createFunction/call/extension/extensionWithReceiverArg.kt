// "/(Create extension function 'List<T>.foo')|(Create extension function 'List.foo')/" "true"
// WITH_STDLIB
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// TODO fix in K2, see KT-67250 getExpectedType() improvements in K2
// IGNORE_K2

class A<T>(val items: List<T>) {
    fun test(): Int {
        return items.<caret>foo<T, Int, String>(2, "2")
    }
}
