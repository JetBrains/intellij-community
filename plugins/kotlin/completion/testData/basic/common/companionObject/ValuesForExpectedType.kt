
class Foo {
    companion object {
        val foo: Foo = Foo()
        val bar: Foo = Foo()
    }
}

val a: Foo = <caret>

// EXIST: { "lookupString":"foo", "itemText":"Foo.foo" }
// EXIST: { "lookupString":"bar", "itemText":"Foo.bar" }