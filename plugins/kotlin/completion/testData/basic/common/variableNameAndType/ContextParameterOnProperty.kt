// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
class Foo
class BarFoo

context(f<caret>)
vat test: Int
    get() = 5

// EXIST: { itemText: "foo: Foo" }
// EXIST: { itemText: "foo: BarFoo" }
// IGNORE_K1