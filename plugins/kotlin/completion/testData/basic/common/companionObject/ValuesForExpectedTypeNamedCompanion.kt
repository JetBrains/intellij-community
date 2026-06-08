class Foo {
    companion object MyCompanion {
        val foo: Foo = Foo()
        val bar: Foo = Foo()
    }
}

val a: Foo = <caret>

// EXIST: { "lookupString":"foo", "itemText":"Foo.MyCompanion.foo" }
// EXIST: { "lookupString":"bar", "itemText":"Foo.MyCompanion.bar" }
