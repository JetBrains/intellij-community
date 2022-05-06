// "Create member function 'foo'" "false"
// ACTION: Add names to call arguments
// ACTION: Do not show return expression hints
// ERROR: Unresolved reference: x

class A<T>(val n: T) {
    fun foo(p: Int) {

    }
}

fun test() {
    A(1).<caret>foo(x)
}