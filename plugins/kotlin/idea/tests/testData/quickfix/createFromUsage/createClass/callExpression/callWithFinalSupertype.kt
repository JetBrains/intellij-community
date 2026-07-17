// "Create class 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

final class A

fun test(): A = <caret>Foo(2, "2")