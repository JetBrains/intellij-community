// "Create class 'Foo'" "true"

open class A {

}

fun test(a: A): A = <caret>Foo<A, Int>(a, 1)
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction