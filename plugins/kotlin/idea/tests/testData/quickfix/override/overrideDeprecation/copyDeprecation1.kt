// "Copy '@Deprecated' annotation from 'Base.foo' to 'Derived.foo'" "true"
// WITH_STDLIB

open class Base {
    @Deprecated("Don't use")
    open fun foo() {}
}

class Derived : Base() {
    override fun <caret>foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix