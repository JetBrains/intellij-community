// "Replace with generated @PublishedApi bridge call '`access$test`(...)'" "true"
// K2_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR

open class ABase {
    protected fun test(p: Int) {
    }


    inline fun test() {
        {
            <caret>test(1)
        }()
    }

    @PublishedApi
    internal fun `access$test`(p: Int) = test(p)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceProtectedToPublishedApiCallFix