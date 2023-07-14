// "Create class 'Foo'" "true"

open class A {

}

fun test(a: A): A = <caret>Foo<A, Int>(a, 1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix