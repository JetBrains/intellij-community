// PROBLEM: none
interface I {
    override fun toString(): String
}

class Test : I {
    <caret>override fun toString() = super.toString()
}