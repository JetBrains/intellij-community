// PROBLEM: Non-canonical modifiers order
open class Base {
    open fun foo() {}
}

open class Test : Base() {
    <caret>override final public fun foo() {}
}
