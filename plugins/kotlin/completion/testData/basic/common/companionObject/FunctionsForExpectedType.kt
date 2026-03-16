
class Foo {
    companion object {
        fun foo(): Foo = Foo()
        fun bar(): Foo = Foo()
    }
}

val a: Foo = <caret>

// EXIST: { "lookupString":"foo", "itemText":"Foo.foo" }
// EXIST: { "lookupString":"bar", "itemText":"Foo.bar" }