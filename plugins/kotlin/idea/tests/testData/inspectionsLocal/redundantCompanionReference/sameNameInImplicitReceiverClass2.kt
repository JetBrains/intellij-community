// PROBLEM: none
class A {
    val foo = "string"
}

class B {
    companion object {
        val foo = 2

        val A.bar: Int
            get() = <caret>Companion.foo
    }
}
