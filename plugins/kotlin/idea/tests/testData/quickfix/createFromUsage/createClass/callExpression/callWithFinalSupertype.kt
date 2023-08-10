// "Create class 'Foo'" "false"
// ACTION: Convert to block body
// ACTION: Create function 'Foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: Foo

final class A

fun test(): A = <caret>Foo(2, "2")