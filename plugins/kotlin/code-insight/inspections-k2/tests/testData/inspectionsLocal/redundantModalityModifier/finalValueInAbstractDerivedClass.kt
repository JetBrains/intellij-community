// PROBLEM: none
interface Interface {
    val gav: Int
        get() = 42
}

abstract class AbstractDerived(override <caret>final val gav: Int) : Interface { }