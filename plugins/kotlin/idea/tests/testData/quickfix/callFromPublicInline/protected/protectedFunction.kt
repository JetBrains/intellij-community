// "Make 'protectedMethod' public" "true"
open class Foo {
    protected fun protectedMethod() {}

    inline fun inlineFun() {
        <caret>protectedMethod()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix