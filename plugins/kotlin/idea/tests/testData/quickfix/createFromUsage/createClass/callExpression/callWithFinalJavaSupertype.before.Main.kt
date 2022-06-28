// "Create class 'Foo'" "false"
// ACTION: Convert to block body
// ACTION: Create function 'Foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: Foo

internal fun test(): A = <caret>Foo(2, "2")