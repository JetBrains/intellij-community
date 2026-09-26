// NO_HINTS
// LANGUAGE_VERSION: 2.3
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