// "Create class 'Foo'" "true"
// ERROR: Unresolved reference: Foo

open class A(val n: Int)

fun test(): A = J.Foo(2, "2")