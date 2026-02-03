// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
class Foo
class BarFoo

context(f<caret>)
fun test() {

}

// EXIST: { itemText: "foo: Foo" }
// EXIST: { itemText: "foo: BarFoo" }
// IGNORE_K1