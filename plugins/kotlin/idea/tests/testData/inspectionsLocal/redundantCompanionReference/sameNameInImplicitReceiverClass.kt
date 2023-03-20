// PROBLEM: none
class A {
    val foo = "string"
}

class B {
    companion object {
        val foo = 2

        fun A.bar(): Int = <caret>Companion.foo
    }
}
