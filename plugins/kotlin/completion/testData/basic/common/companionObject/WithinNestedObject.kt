
class Foo {
    companion object {
        fun foo(): Foo = Foo()
        val bar: Foo = Foo()
    }
    object Inner {
        val a: Foo = <caret>
    }
}

// EXIST: { "lookupString":"foo", "itemText":"foo" }
// EXIST: { "lookupString":"bar", "itemText":"bar" }
// ABSENT: { "lookupString":"foo", "itemText":"Foo.foo" }
// ABSENT: { "lookupString":"bar", "itemText":"Foo.bar" }
// IGNORE_K1