// PROBLEM: none
interface I {
    override fun equals(other: Any?): Boolean
}

open class A

class Test : I, A() {
    <caret>override fun equals(other: Any?) = super.equals(other)
}