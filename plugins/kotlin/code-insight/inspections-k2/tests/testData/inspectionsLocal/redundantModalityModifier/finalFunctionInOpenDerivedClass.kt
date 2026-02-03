// PROBLEM: none
abstract class Base {
    abstract fun bar()
}

open class OpenDerived : Base() {
    override <caret>final fun bar() {}
}