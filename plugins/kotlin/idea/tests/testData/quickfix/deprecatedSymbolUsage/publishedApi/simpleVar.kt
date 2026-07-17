// "Replace with generated @PublishedApi bridge call '`access$prop`'" "true"
// K2_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
annotation class Z

open class ABase {
    @Z
    protected var prop = 1

    inline fun test() {
        {
            <caret>prop
        }()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix