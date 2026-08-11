// "Remove 'inline' modifier" "true"
// K2_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
open class Foo {
    protected fun protectedMethod() {}

    inline val inlineProperty: Int
        get() {
            <caret>protectedMethod()
            return 42
        }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeModifiersFix