// "Create class 'Foo'" "false"
// ACTION: Create function 'Foo'
// ACTION: Convert to block body
// ACTION: Rename reference
// ERROR: Unresolved reference: Foo

final class A

fun test(): A = <caret>Foo(2, "2")