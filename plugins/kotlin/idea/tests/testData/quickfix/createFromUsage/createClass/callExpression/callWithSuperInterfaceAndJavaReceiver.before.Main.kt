// "Create class 'Foo'" "true"
// ERROR: Unresolved reference: Foo
// IGNORE_K1
interface T

fun test(): T = J.<caret>Foo(2, "2")