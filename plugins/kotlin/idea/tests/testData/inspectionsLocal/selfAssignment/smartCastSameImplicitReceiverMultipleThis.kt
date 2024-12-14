// PROBLEM: Variable 'foo' is assigned to itself
// IGNORE_K1

class Foo {
    var foo: Int = 1
}

fun Any.test() {
    if (this is Foo) {
        with(Foo()) {
            foo = <caret>foo
        }
    }
}
