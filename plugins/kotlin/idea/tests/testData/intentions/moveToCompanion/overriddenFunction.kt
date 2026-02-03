// SHOULD_FAIL_WITH: Function <b><code>foo()</code></b> is overridden by declaration(s) in a subclass
// IGNORE_K2
open class A {
    open fun <caret>foo() {

    }
}

class B: A() {
    override fun foo() {

    }
}