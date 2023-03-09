// FIR_COMPARISON
// FIR_IDENTICAL
class Foo
class BarFoo

fun foo(foo: Foo) {}

val f<caret>

// EXIST: { itemText: "foo", tailText: ": Foo (<root>)" }
// ABSENT: { itemText: "foo", tailText: ": BarFoo (<root>)" }