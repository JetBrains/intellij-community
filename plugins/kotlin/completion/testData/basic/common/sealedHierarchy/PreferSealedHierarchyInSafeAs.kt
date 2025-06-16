// IGNORE_K1
class A1
class Z1
sealed class Foo
class A2
class Z2
class Bar: Foo()
class Baz: Foo()
class A3
class Z3


fun f(foo: Foo) {
    val t = foo as? <caret>
}

// WITH_ORDER
// EXIST: Bar
// EXIST: Baz
// EXIST: Foo
// EXIST: A1
// EXIST: A2
// EXIST: A3
// EXIST: Z1
// EXIST: Z2
// EXIST: Z3