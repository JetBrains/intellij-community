// WITH_STDLIB

open class Base {
    @Deprecated(message = "Don't use", replaceWith = ReplaceWith("bar()"))
    open fun foo() {}

    open fun bar() {}
}

class Derived : Base() {
    override fun <caret>foo() {}
}
