// PROBLEM: none
// K2-ERROR: Unresolved reference 'Foo'.
// K2-ERROR: Unresolved reference 'getIfReady'.

fun foo(foo: Foo) {
    val foo2 = foo.getIfReady<caret>()
}