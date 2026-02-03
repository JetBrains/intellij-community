fun test() {
    val foo = Foo(Bar(0))
    return foo.bar.baz
}

class Foo(val bar: Bar)
class Bar(val baz: Int)