// PROBLEM: none
// WITH_STDLIB
class A {
    companion object {
        val INST = A()
    }
}

class B {
    companion object {
        val INST = B()
    }

    fun foo() {
        <caret>A.INST.toString()
    }
}