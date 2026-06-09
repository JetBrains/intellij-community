// PROBLEM: none
open class Base {
    open var foo: Int = 0
}

class Child : Base() {
    override var<caret> foo: Int = 0
        get() = 1
}
