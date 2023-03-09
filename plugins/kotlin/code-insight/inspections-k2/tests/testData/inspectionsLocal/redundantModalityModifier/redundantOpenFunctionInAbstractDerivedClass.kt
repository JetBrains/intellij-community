interface Interface {
    fun foo()
}

abstract class AbstractDerived() : Interface {
    override <caret>open fun foo() {}
}