// PROBLEM: none
interface Interface {
    fun foo()
}

abstract class AbstractDerived : Interface {
    override <caret>final fun foo() {}
}