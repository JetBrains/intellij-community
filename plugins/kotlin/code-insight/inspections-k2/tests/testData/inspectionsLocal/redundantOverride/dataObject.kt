// LANGUAGE_VERSION: 1.9
open class Foo {
    open fun foo() = 1
}

data object D : Foo() {
    <caret>override fun foo(): Int = super.foo()
}