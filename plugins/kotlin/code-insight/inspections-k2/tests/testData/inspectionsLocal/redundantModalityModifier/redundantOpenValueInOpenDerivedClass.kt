abstract class Base {
    open val gav = 42
}

open class OpenDerived : Base() {
    override <caret>open val gav = 13
}