sealed interface Foo {
    class Bar : Foo
    class Baz : Foo
}
class Other: Foo

fun xx(a: Foo) {
}

fun yy() {
    xx(<caret>)
}
// IGNORE_K1
// EXIST: Bar
// EXIST: Baz
// EXIST: Other