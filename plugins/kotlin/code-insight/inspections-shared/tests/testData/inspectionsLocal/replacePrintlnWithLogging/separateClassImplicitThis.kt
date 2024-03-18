// PROBLEM: none

fun foo() {
    with(A()) {
        <caret>println("foo")
    }
}

class A {
    fun println(message: String) {}
}