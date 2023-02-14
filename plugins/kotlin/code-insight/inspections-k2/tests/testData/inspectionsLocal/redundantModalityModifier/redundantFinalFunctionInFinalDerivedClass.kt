abstract class Base {
    abstract fun bar()
}

class FinalDerived : Base() {
    override <caret>final fun bar() {}
}