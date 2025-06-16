// IGNORE_K1
class A1
class Z1
class A2
sealed interface Foo {
    sealed interface Bar : Foo
    sealed interface Baz : Foo
}
class BarImpl : Foo.Bar
class BazImpl : Foo.Baz
class Z2
class A3
class Z3


fun f(foo: Foo) {
    val t = foo as <caret>
}

// WITH_ORDER
// EXIST: Bar
// EXIST: Baz
// EXIST: BarImpl
// EXIST: BazImpl
// EXIST: Foo
// EXIST: A1
// EXIST: A2
// EXIST: A3
// EXIST: Z1
// EXIST: Z2
// EXIST: Z3