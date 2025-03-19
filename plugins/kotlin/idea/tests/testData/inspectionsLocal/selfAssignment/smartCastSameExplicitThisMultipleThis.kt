// PROBLEM: Variable 'foo' is assigned to itself
// WITH_STDLIB
class Foo {
    var foo: Int = 1
}

fun Any.test() {
    if (this is Foo) {
        with(Foo()) {
            this.foo = <caret>this.foo
        }
    }
}
