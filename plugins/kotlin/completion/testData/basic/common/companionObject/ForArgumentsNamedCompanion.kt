class Foo {
    companion object MyCompanion {
        fun foo(): Foo = Foo()
        val baz = Foo()
    }
}

fun f(a: Foo) {

}
fun test() {
    f(<caret>)
}

// EXIST: { "lookupString":"foo", "itemText":"Foo.MyCompanion.foo" }
// EXIST: { "lookupString":"baz", "itemText":"Foo.MyCompanion.baz" }
