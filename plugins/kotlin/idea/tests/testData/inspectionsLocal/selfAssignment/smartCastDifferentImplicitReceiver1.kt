// PROBLEM: none


class Foo {
    var foo: Int = 1
}

fun Any.test() {
    if (this is Foo) {
        foo = 2
        with(Foo()) {
            this@test.foo = <caret>foo
        }
    }
}
