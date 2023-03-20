// PROBLEM: none
interface I {
    override fun equals(other: Any?): Boolean
}

interface I2 {
    override fun equals(other: Any?): Boolean
}

class Test : I, I2 {
    <caret>override fun equals(other: Any?) = super.equals(other)
}