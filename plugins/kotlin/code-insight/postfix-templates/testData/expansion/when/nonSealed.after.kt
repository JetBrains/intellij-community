fun test(f: Foo) {
    f.when
}

class Foo {
    class Bar : Foo()
    class Baz : Foo()
}