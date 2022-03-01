interface I {
    override fun equals(other: Any?): Boolean
}

open class A {
    override fun equals(other: Any?): Boolean = false
}

class Test : I, A() {
    <caret>override fun equals(other: Any?) = super.equals(other)
}