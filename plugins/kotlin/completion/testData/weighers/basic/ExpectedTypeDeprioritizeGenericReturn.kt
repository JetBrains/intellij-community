// FIR_COMPARISON
// FIR_IDENTICAL
class Foo

// This should not appear in the top because we want to not prioritize functions that can return anything
// See: KTIJ-35571
fun <T> returningZAnything(): T = Foo()
fun returningFoo(): Foo = Foo()
fun returningUnrelated(): Int = 5

fun foo(a: Foo) {

}

fun test() {
    foo(returning<caret>)
}

// ORDER: returningFoo
// ORDER: returningUnrelated
// ORDER: returningZAnything