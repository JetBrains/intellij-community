// "Create class 'Foo'" "true"
// ERROR: Unresolved reference: Foo
/* IGNORE_K2 */
interface T

fun test(): T = J.Foo(2, "2")