// PROBLEM: none
// WITH_STDLIB
fun foo() {
    with(A()) {
        <caret>println("foo")
    }
}

class A {
    fun println(message: String) {}
}