interface Interface {
    fun foo()
}

interface Derived : Interface {
    override <caret>open fun foo() {}
}