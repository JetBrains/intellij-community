// PROBLEM: none
data class Foo(val name: String)

object Bar {
    val foo = Foo("foo")
}

object Test {
    val foo = <caret>Bar.foo.copy(name = "foo2")
}