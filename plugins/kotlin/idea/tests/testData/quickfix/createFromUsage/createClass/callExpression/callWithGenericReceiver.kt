// "Create class 'Foo'" "true"
// IGNORE_K2
class A<T>(val n: T) {

}

fun <U> test(u: U) {
    val a = A(u).<caret>Foo(u)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction