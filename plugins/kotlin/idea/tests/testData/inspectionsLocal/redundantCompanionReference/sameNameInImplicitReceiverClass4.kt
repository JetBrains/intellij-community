// PROBLEM: none
// WITH_STDLIB
open class A {
    val foo = "string"
}

class B {
    companion object {
        val foo = 2

        fun bar(): Int = with(A()) {
            <caret>Companion.foo
        }
    }
}