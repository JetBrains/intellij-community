// FIR_COMPARISON
// FIR_IDENTICAL
// COMPILER_ARGUMENTS: -Xcontext-parameters
package test

class Foo

class BarFoo

context(f<caret>)
fun foo() {

}

// ELEMENT_TEXT: foo: Foo
// IGNORE_K1
