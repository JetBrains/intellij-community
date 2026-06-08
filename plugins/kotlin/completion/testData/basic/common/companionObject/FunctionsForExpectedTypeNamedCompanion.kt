class Foo {
    companion object MyCompanion{
        fun foo(): Foo = Foo()
        val baz: Foo = Foo()
    }
}

val a: Foo = <caret>

// EXIST: { "lookupString":"foo", "itemText":"Foo.MyCompanion.foo" }
// EXIST: { "lookupString":"baz", "itemText":"Foo.MyCompanion.baz" }
