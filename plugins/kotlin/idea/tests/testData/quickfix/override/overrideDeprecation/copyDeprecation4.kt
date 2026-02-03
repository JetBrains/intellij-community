// "Copy '@Deprecated' annotation from 'Base.foo' to 'Derived.foo'" "true"
// WITH_STDLIB

open class Base {
    @Deprecated("Don't use", level = DeprecationLevel.HIDDEN, replaceWith = ReplaceWith("bar()", "p", "q"))
    open fun foo() {}

    open fun bar() {}
}

class Derived : Base() {
    override fun <caret>foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix