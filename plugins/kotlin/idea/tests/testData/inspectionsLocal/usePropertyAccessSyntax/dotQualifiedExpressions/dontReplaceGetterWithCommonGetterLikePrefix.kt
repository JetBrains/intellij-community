// PROBLEM: none
// ERROR: Unresolved reference: Foo
// K2_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo(foo: Foo) {
    val foo2 = foo.getIfReady<caret>()
}