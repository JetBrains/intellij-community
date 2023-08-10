fun test() {
    val foo = Foo(Bar(0))
    println(foo.bar.baz)
}

class Foo(val bar: Bar)
class Bar(val baz: Int)