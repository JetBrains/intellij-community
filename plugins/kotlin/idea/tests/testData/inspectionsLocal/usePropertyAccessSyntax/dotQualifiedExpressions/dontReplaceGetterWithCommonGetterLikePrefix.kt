// PROBLEM: none
// ERROR: Unresolved reference: Foo
// K2_ERROR: Unresolved reference 'Foo'.
// K2_ERROR: Unresolved reference 'getIfReady'.

fun foo(foo: Foo) {
    val foo2 = foo.getIfReady<caret>()
}