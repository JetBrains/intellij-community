// PROBLEM: Variable 'foo' is assigned to itself
// IGNORE_K1

interface Foo {
    var foo: Int
}

class Bar : Foo {
    override var foo = 1
}

fun Any.test() {
    if (this is Foo) {
        if (this is Bar) {
            this.foo = <caret>foo
        }
    }
}
