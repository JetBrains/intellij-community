// "Create class 'Foo'" "false"
// ERROR: Unresolved reference: Foo

final class A

fun test(): A = <caret>Foo(2, "2")