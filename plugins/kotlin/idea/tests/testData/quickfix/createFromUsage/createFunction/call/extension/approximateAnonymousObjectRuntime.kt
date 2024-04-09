// "/(Create extension function 'List<Int>.foo')|(Create extension function 'List.foo')/" "true"
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinCallableAction
// TODO fix in K2, see KT-67250 getExpectedType() improvements in K2
// IGNORE_K2

open class A

fun main(args: Array<String>) {
    val list = listOf(1, 2, 4, 5)
    list.<caret>foo { object : A() {} }
}
