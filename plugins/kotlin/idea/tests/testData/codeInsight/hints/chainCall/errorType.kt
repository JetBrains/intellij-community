// NO_HINTS
interface Baz {
    fun foo(): Foo = Foo()
}

class Outer : Baz {
    class Nested {
        val x = this@Outer.foo()
            .bar()
            .foo()
            .bar()
    }
}