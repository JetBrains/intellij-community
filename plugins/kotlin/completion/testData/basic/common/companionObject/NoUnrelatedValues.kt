
class Foo {
    companion object {
        val foo: Foo = Foo()
        val bar: String = ""
    }
}

val a: Foo = <caret>

// EXIST: { "lookupString":"foo", "itemText":"Foo.foo" }
// ABSENT: bar