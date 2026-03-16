
class Foo {
    companion object {
        fun foo(): Foo = Foo()
        fun bar(): Foo = Foo()
    }
}

fun f(a: Foo) {

}
fun test() {
    f(<caret>)
}

// EXIST: { "lookupString":"foo", "itemText":"Foo.foo" }
// EXIST: { "lookupString":"bar", "itemText":"Foo.bar" }