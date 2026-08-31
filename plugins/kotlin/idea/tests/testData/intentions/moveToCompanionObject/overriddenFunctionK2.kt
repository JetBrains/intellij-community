// SHOULD_FAIL_WITH: Function foo() is overridden by declaration(s) in a subclass

open class A {
    open fun <caret>foo() {

    }
}

class B: A() {
    override fun foo() {

    }
}