// FIR_COMPARISON
// FIR_IDENTICAL
class Foo<T>

fun <T> genericFoo(): Foo<T> = TODO()
fun exactFoo(): Foo<Int> = TODO()
fun unrelatedFoo(): Foo<String> = TODO()
// This should not appear in the top because we want to deprioritize functions that can return anything
// See: KTIJ-35571
fun <T> returningAnything(): T = TODO()

fun foo(a: Foo<Int>) {

}

fun test() {
    foo(<caret>)
}

// ORDER: exactFoo
// ORDER: a =
// ORDER: Foo
// ORDER: genericFoo
// ORDER: unrelatedFoo
// IGNORE_K1