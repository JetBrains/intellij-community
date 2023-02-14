// PROBLEM: none
interface I {
    override fun equals(other: Any?): Boolean
}

class Test : I {
    <caret>override fun equals(other: Any?) = super.equals(other)
}