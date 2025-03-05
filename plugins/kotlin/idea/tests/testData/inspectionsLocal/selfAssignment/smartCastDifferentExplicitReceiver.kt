// PROBLEM: none

class Foo {
    var foo: Int = 1
}

fun test(a: Any, b: Any) {
    if (a is Foo && b is Foo) {
        a.foo = <caret>b.foo
    }
}
