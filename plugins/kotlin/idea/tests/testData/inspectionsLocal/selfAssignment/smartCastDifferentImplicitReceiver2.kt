// PROBLEM: none
// IGNORE_K1

class Foo {
    var foo: Int = 1
}

fun Any.test() {
    if (this is Foo) {
        foo = 2
        with(Foo()) {
            foo = <caret>this@test.foo
        }
    }
}
