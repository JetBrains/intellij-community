// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-receivers
class Foo
class BarFoo

context(f<caret>)
fun test() {

}

// ABSENT: { itemText: "foo: Foo" }
// ABSENT: { itemText: "foo: BarFoo" }