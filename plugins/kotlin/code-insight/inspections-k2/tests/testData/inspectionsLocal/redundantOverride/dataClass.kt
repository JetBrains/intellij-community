open class Foo {
    open fun foo() = 1
}

data class D(val i: Int) : Foo() {
    <caret>override fun foo(): Int = super.foo()
}
