// "Copy '@Deprecated' annotation from 'Base.foo' to 'Derived.foo'" "true"
// WITH_STDLIB

open class Base {
    @Deprecated("Don't use", level = DeprecationLevel.ERROR)
    open fun foo() {}
}

class Derived : Base() {
    override fun <caret>foo() {}
}
