// "Remove parameter 'm'" "true"
// DISABLE_ERRORS
fun test() {
    Foo.foo()
    Foo.foo(1<caret>)
    Foo.foo(1, 2)
    Foo.foo(3, 4)
}