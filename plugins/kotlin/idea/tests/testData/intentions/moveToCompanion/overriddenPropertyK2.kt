// SHOULD_FAIL_WITH: Property foo is overridden by declaration(s) in a subclass
// IGNORE_K1
open class A {
    open val <caret>foo: Int = 1
}

class B: A() {
    override val foo: Int = 2
}