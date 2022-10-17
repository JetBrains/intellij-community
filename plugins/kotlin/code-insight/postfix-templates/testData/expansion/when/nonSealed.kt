fun test(f: Foo) {
    f<caret>
}

class Foo {
    class Bar : Foo()
    class Baz : Foo()
}