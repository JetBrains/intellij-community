fun test() {
    val foo = Foo(Bar(0))
    foo.bar<caret>.baz
}

class Foo(val bar: Bar)
class Bar(val baz: Int)