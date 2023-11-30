// "Create member function 'foo'" "false"
// ACTION: Add names to call arguments

class A<T>(val n: T) {
    fun foo(p: Int) {

    }
}

fun test() {
    A(1).<caret>foo(1)
}