// PROBLEM: none

interface Interface {
    abstract val i: Int
    class Impl(override val <caret>i: Int) : Interface {}
}