// FIX: Change to 'val' and delete initializer
open class Base {
    open val foo: Int = 0
}

class Child : Base() {
    override var<caret> foo: Int = 0
        get() = 1
}
