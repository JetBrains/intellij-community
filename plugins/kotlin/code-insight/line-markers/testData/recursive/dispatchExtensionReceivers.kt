class Foo {
    fun Foo.foo(other: Foo) {
        foo(other)
        other.foo(other)

        this.foo(other)
        this@foo.foo(other)
        this@Foo.foo(other)

        with(other) {
            foo(this@foo)
        }
    }
}