package m2.second

import m2.second.Bar.foo

data class Foo(val name: String)

object Bar {
    val foo = Foo("foo")
}

object Test {
    val boo = <caret>Bar.foo.copy(name = "foo2")
}