// "Make 'inlineProperty' protected" "true"
open class Foo {
    protected fun protectedMethod() {}

    inline val inlineProperty: Int
        get() {
            <caret>protectedMethod()
            return 42
        }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix