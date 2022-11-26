interface Interface {
    val gav: Int
        get() = 42
}

abstract class AbstractDerived : Interface {
    override <caret>open val gav = 13
}