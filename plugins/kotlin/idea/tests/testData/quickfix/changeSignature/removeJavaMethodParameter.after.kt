// "Remove parameter 'm'" "true"
// DISABLE_ERRORS
fun test() {
    Foo.foo()
    Foo.foo(1<caret>)
    Foo.foo(1)
    Foo.foo(3)
}