// WITH_STDLIB

open class Base {
    @Deprecated("Don't use", level = DeprecationLevel.HIDDEN, replaceWith = ReplaceWith("bar()", "p", "q"))
    open fun foo() {}

    open fun bar() {}
}

class Derived : Base() {
    override fun <caret>foo() {}
}
