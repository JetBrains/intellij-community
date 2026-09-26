class Foo {
    companion object {
        val foo: Foo = Foo()
        val bar: String = ""
        fun baz: Foo = Foo()
        fun qux = ""
    }
}

val a: Foo = <caret>

// EXIST: { "lookupString":"foo", "itemText":"Foo.foo" }
// EXIST: { "lookupString":"baz", "itemText":"Foo.baz" }
// ABSENT: bar
// ABSENT: qux