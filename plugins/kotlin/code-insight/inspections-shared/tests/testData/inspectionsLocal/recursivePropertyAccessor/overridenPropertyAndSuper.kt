// PROBLEM: none

open class Base {
    open val x: Int = 1
}

class Derived(): Base() {
    override val x: Int
        get() {
            return super.x<caret>
        }
}
