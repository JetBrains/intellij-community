// "Copy '@Deprecated' annotation from 'Base.x' to 'Derived.x'" "true"
// WITH_STDLIB

open class Base {
    @Deprecated("Don't use", level = DeprecationLevel.ERROR)
    open var x: Int = 0
}

class Derived : Base() {
    override var <caret>x: Int
        get() = 0
        set(value) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CopyDeprecatedAnnotationFix