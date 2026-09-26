fun test(f: Foo) {
    when (f) {
         -> {}
        else -> {}
    }
}

class Foo {
    class Bar : Foo()
    class Baz : Foo()
}