class Foo {
    fun <caret>len(x: Int) = hashCode() + x
}

val unbound: (Foo, Int) -> Int = Foo::len
