// PROBLEM: none

fun foo() {
    A().<caret>println("foo")
}

class A {
    fun println(message: String) {}
}