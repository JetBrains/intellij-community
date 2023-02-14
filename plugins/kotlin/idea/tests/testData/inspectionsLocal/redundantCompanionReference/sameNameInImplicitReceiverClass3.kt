// PROBLEM: none
open class A {
    val foo = "string"
}

class B {
    companion object {
        val foo = 2

        fun <T : A> T.bar(): Int {
            return <caret>Companion.foo
        }
    }
}
